FROM postgres:11

ENV POSTGRES_DB world
ENV POSTGRES_USER jimmy
ENV POSTGRES_PASSWORD banana

ADD world.sql /docker-entrypoint-initdb.d/
ADD server.crt /var/lib/postgresql/
ADD server.key /var/lib/postgresql/
RUN chown postgres /var/lib/postgresql/server.key && chmod 600 /var/lib/postgresql/server.key

