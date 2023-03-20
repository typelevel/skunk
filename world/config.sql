ALTER SYSTEM SET ssl_cert_file TO '/var/lib/postgresql/server.crt';
ALTER SYSTEM SET ssl_key_file TO '/var/lib/postgresql/server.key';
ALTER SYSTEM SET ssl TO 'ON';