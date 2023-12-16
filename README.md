# Simple Java Chat Server and Client

This was only tested on Windows. Im not sure where parts of the code were sourced from.

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

