# Prometheus Watcher

This project is a part of a larger project, specifically, my undergraduate dissertation. 

This component of the application takes a set of user defined queries, along with some accompanying information and repeatedly queries Prometheus with the query string provided. If the query result exceeds the specified threshold for this query, a Kafka message is produced to a topic that will then be consumed by the second component of the application (responsible for triggering a scale of a deployment in k8's). 
