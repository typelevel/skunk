FROM postgres:11
ENV POSTGRES_DB world
ENV POSTGRES_USER jimmy
ENV POSTGRES_PASSWORD banana
ADD fix_perms.sh /docker-entrypoint-initdb.d/
ADD world.sql /docker-entrypoint-initdb.d/
ADD ltree.sql /docker-entrypoint-initdb.d/
ADD config.sql /docker-entrypoint-initdb.d/
