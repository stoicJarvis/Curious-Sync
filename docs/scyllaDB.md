1. Run a ScyllaDB container locally and expose the default CQL binary port (9042):
   `docker run --name scylla-local -p 9042:9042 -d scylladb/scylla:latest --smp 6 --memory 4G --overprovisioned 1`
   (`--smp N` sets how many shards/CPU cores Scylla uses — this is the single biggest lever for write
   throughput since Scylla is shard-per-core. `--smp 1` runs it as effectively single-threaded, capping
   throughput far below what Scylla can do. `--overprovisioned 1` tells Scylla it's sharing the host with
   other processes, which is required on a laptop/dev machine. Tune `--smp`/`--memory` to leave a couple
   of cores/GB free for the OS, Docker Desktop, and the Spring Boot app.)

2. interact with cql
```
    docker exec -it scylla-local cqlsh
```

3. check databases [Database = KEYSPACES in scylla/casandra]
```
    DESCRIBE KEYSPACES;
```

4. create keyspace
```
    CREATE KEYSPACE dev_sandbox
    WITH replication = {
        'class': 'NetworkTopologyStrategy',
        'datacenter1': 1
    };
```

5.  drop keyspace
```
    DROP KEYSPACE my_keyspace;
```

6. use keyspace
```
    USE dev_sandbox;
```

7. create table
```
    CREATE TABLE post_likes (
        postId uuid,
        userId uuid,
        liked_at timestamp,
        PRIMARY KEY (postId, userId)
    );
```

8. show tables
```
    DESCRIBE tables;
```

9. insert data
```
    INSERT INTO post_likes (postId, userId, liked_at)
    VALUES (63200742-e1d5-472e-8367-bfeb43848123, 11111111-1111-1111-1111-111111111111, toTimestamp(now()));

    INSERT INTO post_likes (postId, userId, liked_at)
    VALUES (63200742-e1d5-472e-8367-bfeb43848123, 22222222-2222-2222-2222-222222222222, toTimestamp(now()));
```

10. select query
```
    SELECT * FROM post_likes WHERE postId = 63200742-e1d5-472e-8367-bfeb43848123;
```
