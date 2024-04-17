# Changes

## 2024-04-17 / 1.0.0

### Feature

- Provide requestId in promtail logs

### Breaking

- Make sure the promtail logback logs regex is also adapted on the Kubernetes 
  environment. Also provide this deployment note in the project when using this
  version of lovely-micronaut.
- regex:
    expression: '^(?s)\[(?P<time>\S+)\] \[(?P<thread>\S+)\] \[(?P<level>\S+)\s?\] \[(?P<logger>\S+)\] \[(?P<requestid>\S*)\] - (?P<message>.*)$'

## 2024-03-01 / 0.5.0

### Feature

- allow to pass kafka image/version to @KafkaTest annotation

## 2023-12-04 / 0.4.0

### Development

- publish sources & javadocs

## 2023-11-17 / 0.3.0

### Feature

- upgrade to  micronaut 4.xx
- upgrade to kotlin 1.9.10

## 2023-05-30 / 0.2.0

### Feature

- add test Fixtures source set and add KafkaTestContainer that uses Kraft

## 2023-04-28 / 0.1.0

- introduced changelog and semantic versioning
