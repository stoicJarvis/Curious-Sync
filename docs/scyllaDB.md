1. Run a ScyllaDB container locally and expose the default CQL binary port (9042):
   `docker run --name scylla-local -p 9042:9042 -d scylladb/scylla:latest --smp 1`
   (Note: --smp 1 allocates 1 CPU thread to prevent ScyllaDB from claiming all CPU cores on your laptop).

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
        post_id uuid,
        user_id uuid,
        liked_at timestamp,
        PRIMARY KEY (post_id, user_id)
    );
```

8. show tables
```
    DESCRIBE tables;
```

9. insert data
```
    INSERT INTO post_likes (post_id, user_id, liked_at)
    VALUES (63200742-e1d5-472e-8367-bfeb43848123, 11111111-1111-1111-1111-111111111111, toTimestamp(now()));

    INSERT INTO post_likes (post_id, user_id, liked_at)
    VALUES (63200742-e1d5-472e-8367-bfeb43848123, 22222222-2222-2222-2222-222222222222, toTimestamp(now()));
```

10. select query
```
    SELECT * FROM post_likes WHERE post_id = 63200742-e1d5-472e-8367-bfeb43848123;
```
