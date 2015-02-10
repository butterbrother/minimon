minimon
=======

_ Please not expect much from this utility. It is written as a practical work in the study of language.  
Use professional open or close solutions if you need a really high-quality monitoring._

Automatical micro monitoring system.  
Writed on Java, it use default GNU/Linux or Sun/Oracle Solaris system utilites natively (serves as the shell)
to verify remote and local sources:
- remote host (with ping)
- remote web-sources (with wget)
- local process (with ps)
- local free space

Also it use ojdbc/sqljdbc jdbc-drivers from connect and check databases.

All probes run separately, eatch in its own threads. Each has its own option period of verifications.  
First performed a standard test, and then in case of failure are performed several rescanning (filtering false positives).
All accidents are notified by e-mail.

Basic settings configure with settings editor (-e parameter). Setting placed in settings.xml.  
Probes settings placed in csv-type files:
- ping.csv - ping
- http.csv - web-test
- db.csv - database test
- proc.csv - local process avaliability test
- free.csv - free space test

ping.csv:
Contains only remote hosts/IP, a column.  
Example:  
ya.ru  
google.ru

http.csv:
All settings start with prefix:
- url: - site url or IP. Must be set.  
- login: - login, if use autentication
- password: - password
- search: - search phrase on site. If this parameter set - be searched phras on this page. If searched - OK, source good.

If no parameter - check only the wget return code  
If set login: you also must set password: parameter  
Example:  
url:example.com  
url:test.com;login:123;password:345;search:Test page

db.csv:  
All settings start with prefix:  
- type: - database type. "oracle" if Oracle(c) database, "ms" if Microsoft(c) database. Must be set.
- url: - server IP or url. Must be set.
- login: and password: - login and password, must be set
- port: - port, optionally
- instance: or sid: - server instance or id. Optional. If not set - use XE/empty
- sql: - custom sql query. If not set - execute query to get system date-time
- result: - expected result in first row first column. Must be set if set sql:

Example: 
url:testdb.com;type:oracle;login:test;password:test  
url:testdb.com;type:oracle;login:test;password:test;instance:testdb;sql:select * from test.test;result:ok

proc.csv:  
Contains process names. A column  
Example:  
jdk  
skype

free.csv:  
Contains mountpoint of partition names.  
All settings start with prefix:
dir: prefix contains mount point, or dir in partition, or file in partition. Must be set.
minimum: prefix contains minimal free space in kb. Must be set.  
Example:  
dir:/;minimum:102400  
dir:/home;minimum:40960  
