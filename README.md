# Simple Java Chat Server and Client

I'm not sure where the Server and Client connection and all messaging code are sourced from. The JSON and other commands are what I added.

- `!?` to get commands.
- `!w [user]` to whisper/private message.
- `!u` to get all users.
- `!n` to get your username.

## Compile

    `javac -cp json-simple-1.1.1.jar *.java`

## Run 

- On Windows:
    `java -cp "json-simple-1.1.1.jar;." ChatClient`
    `java -cp "json-simple-1.1.1.jar;." ChatServer`

- On Linux:
    `java -cp "json-simple-1.1.1.jar:." ChatClient`
    `java -cp "json-simple-1.1.1.jar:." ChatServer`

