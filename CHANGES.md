# Changes

## Unreleased

### Feature

- Provide requestId in promtail logs
- Use JSON logging in promtail logback config
- Upgrade Micronaut + gradle plugin to 4.3.7
- Upgrade Kotlin to 1.9.23

### Breaking

- Make sure the promtail logback logs config is adapted on the Kubernetes 
  environment. Should now use JSON logging, no parsing required anymore.
- Also provide this deployment note in the project when using this
  version of lovely-micronaut.

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
