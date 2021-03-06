package net.iponweb.disthene.reader.handler;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import io.netty.handler.codec.http.*;
import net.iponweb.disthene.reader.beans.TimeSeries;
import net.iponweb.disthene.reader.config.ReaderConfiguration;
import net.iponweb.disthene.reader.exceptions.*;
import net.iponweb.disthene.reader.format.ResponseFormatter;
import net.iponweb.disthene.reader.graphite.Target;
import net.iponweb.disthene.reader.graphite.evaluation.TargetVisitor;
import net.iponweb.disthene.reader.graphite.evaluation.EvaluationContext;
import net.iponweb.disthene.reader.graphite.evaluation.TargetEvaluator;
import net.iponweb.disthene.reader.graphite.grammar.GraphiteLexer;
import net.iponweb.disthene.reader.graphite.grammar.GraphiteParser;
import net.iponweb.disthene.reader.graphite.utils.ValueFormatter;
import com.dark.graphene.reader.handler.RenderParameter;
import net.iponweb.disthene.reader.service.index.ElasticsearchIndexService;
import net.iponweb.disthene.reader.service.metric.CassandraMetricService;
import net.iponweb.disthene.reader.service.stats.StatsService;
import net.iponweb.disthene.reader.service.throttling.ThrottlingService;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author Andrei Ivanov
 */
@Component
public class RenderHandler {

    final static Logger logger = Logger.getLogger(RenderHandler.class);

    private TargetEvaluator evaluator;
    private StatsService statsService;
    private ThrottlingService throttlingService;
    private ReaderConfiguration readerConfiguration;

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private TimeLimiter timeLimiter = new SimpleTimeLimiter(executor);


    public RenderHandler(CassandraMetricService cassandraMetricService, ElasticsearchIndexService elasticsearchIndexService, StatsService statsService, ThrottlingService throttlingService, ReaderConfiguration readerConfiguration) {
        this.evaluator = new TargetEvaluator(cassandraMetricService, elasticsearchIndexService);
        this.statsService = statsService;
        this.throttlingService = throttlingService;
        this.readerConfiguration = readerConfiguration;
    }

    public FullHttpResponse handle(RenderParameter parameters) throws ParameterParsingException, ExecutionException, InterruptedException, EvaluationException, LogarithmicScaleNotAllowed {
        logger.debug("Redner Got request: " + parameters + " / parameters: " + parameters.toString());
        Stopwatch timer = Stopwatch.createStarted();

        double throttled = throttlingService.throttle(parameters.getTenant());

        statsService.incRenderRequests(parameters.getTenant());

        if (throttled > 0) {
            statsService.incThrottleTime(parameters.getTenant(), throttled);
        }

        final List<Target> targets = new ArrayList<>();

        EvaluationContext context = new EvaluationContext(
                readerConfiguration.isHumanReadableNumbers() ? ValueFormatter.getInstance(parameters.getFormat()) : ValueFormatter.getInstance(ValueFormatter.ValueFormatterType.MACHINE)
        );

        // Let's parse the targets
        for(String targetString : parameters.getTargets()) {
            GraphiteLexer lexer = new GraphiteLexer(new ANTLRInputStream(targetString));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            GraphiteParser parser = new GraphiteParser(tokens);
            ParseTree tree = parser.expression();
            try {
                targets.add(new TargetVisitor(parameters.getTenant(), parameters.getFrom(), parameters.getUntil(), context).visit(tree));
            } catch (ParseCancellationException e) {
                String additionalInfo = null;
                if (e.getMessage() != null) additionalInfo = e.getMessage();
                if (e.getCause() != null) additionalInfo = e.getCause().getMessage();
                throw new InvalidParameterValueException("Could not parse target: " + targetString + " (" + additionalInfo + ")");
            }
        }

        logger.info("targets : " + targets);
        FullHttpResponse response;
        try {
            response = timeLimiter.callWithTimeout(new Callable<FullHttpResponse>() {
                @Override
                public FullHttpResponse call() throws EvaluationException, LogarithmicScaleNotAllowed {
                    return handleInternal(targets, parameters);
                }
            }, readerConfiguration.getRequestTimeout(), TimeUnit.SECONDS, true);
        } catch (UncheckedTimeoutException e) {
            logger.debug("Request timed out: " + parameters);
            statsService.incTimedOutRequests(parameters.getTenant());
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
        } catch (EvaluationException | LogarithmicScaleNotAllowed e) {
            throw e;
        } catch (Exception e) {
            logger.error(e);
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }

        timer.stop();
        logger.debug("Request took " + timer.elapsed(TimeUnit.MILLISECONDS) + " milliseconds (" + parameters + ")");

        logger.info("Response status : " + response.getStatus() + " Response Body : " + response.content().toString());
        return response;
    }
    private FullHttpResponse handleInternal(List<Target> targets, RenderParameter parameters) throws EvaluationException, LogarithmicScaleNotAllowed {
        // now evaluate each target producing list of TimeSeries
        List<TimeSeries> results = new ArrayList<>();

        for(Target target : targets) {
            List<TimeSeries> eval = evaluator.eval(target);
            logger.info("TimeSeries : " + eval);
            results.addAll(eval);
        }

        return ResponseFormatter.formatResponse(results, parameters);
    }

}
