## Requirements To build and run the tool

- JDK >= 1.8, 
- Maven >= 3.x

## Building

To build the project use standard mvn commands. Build standard package 

```
mvn clean package 
```

This will build into a single assembly executable. The tool can then be executed using 

## Running the tool

```sh
java -jar target/json-schema-mapping-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Configuration 

The tool can be used to generate multiple groups of schemas as needed for particular purposes. Here is a sample
configuration file that generates for only us-east-2 region. It create 2 groups in addition to the default bundle
based on the config file show below in the local directory ./cfn-schemas

```yaml
settings:
  regions: [us-east-2]
  output: cfn-schemas

groups:
  serverless:
    includes:
      - AWS::ApiGateway.*
      - AWS::Lambda.*
      - AWS::IAM.*

  3tierAsgApp:
    includes:
      - AWS::ElasticLoadBalancingV2.*
      - AWS::RDS.*
      - AWS::AutoScal.*
      - AWS::IAM.*

```

To generate you would save the file as cfg.yml and then run with 

```sh
java -jar target/json-schema-mapping-1.0-SNAPSHOT-jar-with-dependencies.jar --config-file cfg.yml
```

the files will be generated inside $PWD/cfn-schemas/us-east-2/\*-spec.json

