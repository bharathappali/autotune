These API utils (will be interfaces) are the channels for interacting with autotune for the metric related information.

AT EXPERIMENT LEVEL:

    DATASOURCE RELATED:

`addDatasource(DataSource) :`

This Abstraction is to add the datasource to autotune to query the metrics which are passed to it at a trial level
This method receives a Data source entity which has Datasource name, location (path or url) and type (queryable, file based, API based etc)
This method checks if datasource is reachable via checkDatasourceAvaliability and returns status “OK” if success and status “failed” on failure

INPUT JSON:
```
{
    “name”: “Prometheus”,
    “Type”: “Queryable”,
    “Location”: “datasource url”
}
```
OUTPUT JSON:
```
{
    “Status”: “OK”
}
```

`getDataSourceInfo(DataSource Name): `

This Abstraction is to get the information of a particular datasource
We supply the name of the datasource as the parameter and we get the info object which have the details like name, type and location
This method returns the datasource info if autotune has it, else it returns status as “failure” and error as “Data source not found”

INPUT JSON:
```
{
    “Name”: “Prometheus”
}
```
OUTPUT JSON:
```
{
    “name”: “Prometheus”,
    “Type”: “Queryable”,
    “Location”: “datasource url”
}
```

`getDatasourceList():`

This Abstraction returns the list of datasources available in autotune

OUTPUT JSON:
```
{
    “Datasources” : [
        “name”: “Prometheus”,
        “Type”: “Queryable”,
        “Location”: “datasource url”
    ]
}
```

`checkDatasourceAvaliability(datasource name):`

This abstraction checks if the datasource is reachable
This returns “isAvailable” as true if the datasource is reachable and false if the datasource is not reachable

INPUT JSON:
```
{
    “Name”: “Prometheus”
}
```
OUTPUT JSON:
```
{
    “isAvailable”: “True”
}
```


    QUERY RELATED:

`Validate Query (datasource name, query):`

This abstraction validates if a query gets executed without any error
We supply the datasource name and query or request
We call the datasource executor service to run the query and check if it’s not a failure
If the query gets executed we send the data object returned by the executor service and update status as “OK” if the query fails we set the status as “failure”

INPUT JSON:
```
{
    “Name”: “Prometheus”
    “query ”: “query”
}
```
OUTPUT JSON:
```
{
    “Status”: “OK”
    “Data”:  “data object from the service”
}
```

`generateSubQueries(datasource, raw_query):`

This abstraction returns the sub queries extracted from a raw query
This returns the list of sub queries and if their result needs calculation

INPUT JSON:
```
{
    “Name”: “Prometheus”
    “query ”: “query”
}
```
OUTPUT JSON:
```
{
    subQueries: {
        “Min” : {
        “Query” : “query”
        “needsCalculation”: true“”
    }
    …
    “Percentiles”: {
        “50”: {
            “Query”: “query”,
            “needsCalculation”: false
            }
        }
    }
}
```

`getQueriesForModule(datasource, module):`

This abstraction returns the list of queries of a module (cpu, memory, etc)
This returns a JSON listing the queries used in a particular module

INPUT JSON:
```
{
    “Name”: “Prometheus”
    “module ”: “cpu”
}
```
OUTPUT JSON:
```
{
    Queries : [
        “Container_cpu_total_seconds”
        …
    ]
}
```

`getDataPoint(datasource, query, datapoint, value):`

This abstraction returns the required datapoint (min, max, quantiles) value of a particular query
This internally calls the generateSubquery module based on the datasource provided and run the returned query using the datasource executor
If the data point is quantile then we consider value in the value parameter to generate the information

INPUT JSON:
```
{
    “Name”: “Prometheus”
    “query ”: “container_cpu_seconds”
    “Datapoint”: “min”
}
```
OUTPUT JSON:
```
{
    Result: “result”
}
```

    METRIC RELATED

getCPUInfo

getMemInfo

getNetworkInfo

These abstractions need to be classified based on the module type to queries map prepopulated in the executor

The steps for implementation goes on like:
```
    - Check the datasource availability
    - Get list of queries related to the module
    - Run them using the datasource executor
    - Collect the metric results and group them to return
```

