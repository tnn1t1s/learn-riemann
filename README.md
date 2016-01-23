# learn-riemann

me learning Riemann.

## Installing Riemann.

Before getting started, install Riemann as per http://riemann.io/quickstart.html

## The Basics
Now start Riemann. Riemann takes a Riemann config file as its first argument. The files is Clojure program that describes how to process Riemann events. In this section, we'll go through the basics of a Riemann config file. When we're done, you will be able to write your own basic configs.

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
