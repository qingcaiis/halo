Please add the corresponding e2e (aka end-to-end) test cases if you add or update APIs.

## How to work
* Start and watch the [docker-compose](https://docs.docker.com/compose/) via [the script](start.sh)
  * It has three containers: database, Halo, and testing
* Run the e2e testing via [api-testing](https://github.com/LinuxSuRen/api-testing)
  * It will run the test cases from top to bottom
  * You can add the necessary asserts to it

## Run locally
Please follow these steps if you want to run the e2e testing locally.

> Please make sure you have installed docker-compose v2

* Build project via `./gradlew clean build -x check` in root directory of this repository
* Build image via `docker build . -t ghcr.io/halo-dev/halo-dev:main`
* Change the directory to `e2e`, then execute `./start.sh`

## Run Halo only
Please run the following command if you only want to run Halo.

```shell
docker-compose up halo
```

## Run locally with MySQL + Redis (build halo image from this repo)

This repository includes a convenience compose file `compose-mysql-redis.yaml` that will build the Halo image from the project `Dockerfile` and start MySQL + Redis.

Steps:

1. Build the executable jar for the `application` module (the Dockerfile expects jar in `application/build/libs`):

```bash
./gradlew :application:bootJar -x test
```

2. From the `e2e` directory, build and start the compose stack:

```bash
cd e2e
docker compose -f compose-mysql-redis.yaml up --build -d
```

3. Follow logs for the `halo` service:

```bash
docker compose -f compose-mysql-redis.yaml logs -f halo
```

4. Stop and remove the stack (and volumes):

```bash
docker compose -f compose-mysql-redis.yaml down -v
```

Notes:
- The compose file sets `HALO_CACHE_TYPE=redis` (you can remove it to use the default in-memory cache).
- To also enable Spring Session backed by Redis, add the `spring-session-data-redis` dependency to the `application` module and run with `spring.session.store-type=redis`.

