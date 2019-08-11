##### Main configuration in disthene.yaml
```
reader:
# bind address and port
  bind: "0.0.0.0"
  port: 8080
# rollups - currently only "s" units supported  
  rollups:
    - 60s:5356800s
    - 900s:62208000s
store:
# C* contact points, port, keyspace and table
  cluster:
    - "cassandra-1"
    - "cassandra-2"
  port: 9042
  keyspace: 'metric'
  columnFamily: 'metric'
# maximum connections per host , timeouts in seconds, max requests per host - these are literally used in C* java driver settings
  maxConnections: 2048
  readTimeout: 10
  connectTimeout: 10
  maxRequests: 128
index:
# ES cluster name, contact points, native port, index name & type
  name: "disthene"
  cluster:
    - "es-1"
    - "es-2"
  port: 9300
  index: "disthene"
  type: "path"
# Maxim number paths allowed per one wildcard. This is just to prevent abuse
  maxPaths: 50000
stats:
# flush self metrics every 'interval' seconds
  interval: 60
# tenant to use for stats
  tenant: "graphite"
# hostname to use
  hostname: "disthene-reader"
# carbon server to send stats to
  carbonHost: "carbon.example.net"
# carbon port to send stats to
  carbonPort: 2003  
```

##### Logging configuration in disthene-reader-log4j.xml
Configuration is straight forward as per log4j