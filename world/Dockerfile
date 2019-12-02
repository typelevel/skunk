FROM postgres:11
ENV POSTGRES_DB world
ENV POSTGRES_USER jimmy
ENV POSTGRES_PASSWORD banana
ADD world.sql /docker-entrypoint-initdb.d/
