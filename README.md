# Soia Kotlin example

Example showing how to use soia's [Kotlin code generator](https://github.com/gepheum/soia-kotlin-gen) in a project.

## Build and run the example

```shell
# Download this repository
git clone https://github.com/gepheum/soia-kotlin-example.git

cd soia-kotlin-example

# Install all dependencies, which include the soia compiler and the soia
# Kotlin code generator
npm i

npm run run:snippets
# Same as:
#   npm run build  # .soia to .kt codegen
#   ./gradlew run
```

### Start a soia service

From one process, run:
```shell
npm run run:start-service
#  Same as:
#    npm run build
#    ./gradlew run -PmainClass=examples.startservice.StartServiceKt
```

From another process, run:
```shell
npm run run:call-service
#  Same as:
#    npm run build
#    ./gradlew run -PmainClass=examples.callservice.CallServiceKt
```
# soia-kotlin-example
