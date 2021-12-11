## Getting started

    mvn clean jetty:run-exploded \
      -DpublicURI=http://localhost:8888/OSLC4JRegistry \
      -DregistryURI=http://localhost:8888/OSLC4JRegistry/catalog/singleton


URIs you can open:

- http://localhost:8888/OSLC4JRegistry/ (Wink entry page)
- http://localhost:8888/OSLC4JRegistry/catalog/singleton 
  - same as http://localhost:8888/OSLC4JRegistry/catalog, use the one with `/singleton`
- http://localhost:8888/OSLC4JRegistry/admin
  - http://localhost:8888/OSLC4JRegistry/admin?doc=resources
  - http://localhost:8888/OSLC4JRegistry/admin?doc=registry
- http://localhost:8888/OSLC4JRegistry/serviceProviders
  - OOTB, there is only one: http://localhost:8888/OSLC4JRegistry/serviceProviders/1
- http://localhost:8888/OSLC4JRegistry/resourceShapes
  - http://localhost:8888/OSLC4JRegistry/resourceShapes/serviceProviderCatalog
  - http://localhost:8888/OSLC4JRegistry/resourceShapes/serviceProvider
  - http://localhost:8888/OSLC4JRegistry/resourceShapes/service
  - http://localhost:8888/OSLC4JRegistry/resourceShapes/oauthConfiguration
  - http://localhost:8888/OSLC4JRegistry/resourceShapes/publisher
  - http://localhost:8888/OSLC4JRegistry/resourceShapes/prefixDefinition

OSLC Creation Factories:

- http://localhost:8888/OSLC4JRegistry/serviceProviders (HTTP POST + OSLC CF)
- 
