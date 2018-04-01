# SimpleRxWebSocket

Simple wrapper around java web sockets with RxJava.

## Contains
It contains implementations for client web socket endpoint and also for server web socket endpoint. Both support Observer and Observable connections. The act like special version of Subject.

## Usage
Usage was aimed to be simple as is can be. You need to use only one class and you have immediately Observable or entity, to which you can subscribe.

### Client endpoint
For use of reactive client web socket, just create an instance of ```ReactiveClientWebSocket``` with uri and expected type of class. The client will connect, when some Observer subscribes to it or is client subscribed to some Observable.

``` java
// Create observable for sending data to server socket.
final Subject<Entity> data = PublishSubject.create();

// Set up client socket to connect
final String uri = "ws://127.0.0.1:58080/reactive";
final ReactiveClientWebSocket<Entity> webSocket = new ReactiveClientWebSocket<Entity>(uri, Entity.class);

// Subscribe to send data to server socket
data.subscribe(webSocket);

// Subscribe to print data from server socket.
webSocket
    .map(Entity::getNumber)
    .subscribe(new PrintConsumer());

// Send some data
data.onNext(new Entity("Client 4", 4));
data.onNext(new Entity("Client 3", 3));

// Wait for user to end.
System.in.read();

// Cleanup
webSocket.close();
```

### Server endpoint
For using reactive server web socket endpoint you must create a subclass of ```ReactiveServerWebSocket```.

``` java
// Create server web socket endpoint
final ReactiveServerWebSocket serverSocket = ...;

// Create observable for sending data to client sockets.
final Subject<Entity> data = PublishSubject.create();

// Subscribe to send data to client socket. It will broadcast recieved emitted entity to all connected clients.
// To change this behaviour, override onNext method.
data.subscribe(serverSocket);

// Subscribe to print data from client sockets.
serverSocket
    .map(Entity::getMsg)
    .subscribe(new PrintConsumer());
```
