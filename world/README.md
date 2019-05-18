### World Docker Image

Suuuper simple but I can never remember how anything works. To build it

    docker build -t tpolecat/skunk-world:latest .

and to push it

    docker login
    docker push tpolecat/skunk-world:latest

and to run it

    docker run -p5432:5432 -d tpolecat/skunk-world

Note that it takes a few seconds to start up completely since it has to init the db.

If you don't have `psql` and want to run it in a way that lets you connect with container-based psql you can do it this way.

    docker network create skunknet
    docker run -p5432:5432 -d --name skunkdb --network skunknet tpolecat/skunk-world

and then

    docker network create skunknet
    docker run -it --rm --network skunknet postgres psql -h skunkdb -U postgres

to connect with `psql`.

