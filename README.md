# learn-riemann

Learning Riemann by example.

## Installing Riemann.

Install Riemann using the getting started guide here, http://riemann.io/quickstart.html

## The Basics
Riemann takes a Riemann config file as its first argument. The config file is a Clojure program that describes how to process Riemann events. In this section, we'll go through the basics of a Riemann config file. When we're done, you will be able to write your own basic configs.

More about Riemann
 * https://github.com/aphyr/riemann

You can start Riemann with the following command:

$ riemann examples/config/riemann-1.config

## Riemann Streams
Restart Riemann with the following command:

$ riemann examples/config/riemann-2.config

This configuration file demonstrates the basics of Riemann streams. The Riemann engine applies each incoming event to a series of streams. 'streams' is a function that takes a variable number of arguments, each consisting of a function that accepts a map of events.

## 

## License

Copyright © 2015 DJP. Private Repository.
