# Skyler Meadors Forex-eff

## Thought Process
One issue I could not find a solution to, was how to access the ActorSystem for the AkkaHTTP client.  If I were to spend
more time on it, I would have liked to have found a fix for it.  I also went with a simple map cache for the results from
the service call.  I didn't feel it was worth trying to implement a caching library.  Overall the majority of my time was
spent trying to understand all of the abstraction that was taking place.

## How to run and test
Run the Main.scala class, and test using the endpoint http://localhost:8888/rates?from=???&to=???