# Exposure #
A simple self-hosted image gallery written in React using Scala + Servlets on the backend.

## Requirements ##
+ Java 1.8 + Servlet Container (e.g. Tomcat).

## Installation ##

+ Download the `war` archive from the releases page.
+ Deploy the web application inside a servlet container, e.g. Tomcat.
+ Edit `web.xml` and update the parameters `albumTitle`, `albumRootPath`, and `cacheRootPath`.
    + `albumTitle` is the name used for the root folder.
    + `albumRootPath` is the path to the root folder containing all the images.
    + `cacheRootPath` is the path to a writable folder where thumbnails are to be stored.
    + All three parameters are required.
+ Restart the web application.
