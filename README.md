# dbfs
A Java server to measure sound from a web sockets client

To create a WebSocket server in Java that reads dBFS values from a microphone and broadcasts these to connected clients, you can use Java’s WebSocket API for the server and the Java Sound API to capture audio. Here’s how you might set it up:

Prerequisites

	1.	Java Sound API: Used to capture audio from the microphone and calculate dBFS (decibels relative to full scale).
	2.	Java WebSocket API: Used to handle WebSocket connections and broadcast messages to all connected clients. For simplicity, we can use javax.websocket for the WebSocket server implementation.

Implementation Steps

	1.	Capture Audio Data: Use TargetDataLine from Java Sound API to capture real-time audio data.
	2.	Compute dBFS: Compute the dBFS value from the audio sample data.
	3.	WebSocket Server: Create a WebSocket server that can broadcast dBFS values to all connected clients.

Code

Here’s a basic implementation of the above logic:

import javax.sound.sampled.*;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@ServerEndpoint("/audio")
public class AudioWebSocketServer {
    private static Set<Session> sessions = new CopyOnWriteArraySet<>();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        throwable.printStackTrace();
    }

    public static void broadcast(String message) {
        for (Session session : sessions) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        // Start capturing audio and broadcasting dBFS values
        new Thread(AudioWebSocketServer::captureAndBroadcastAudio).start();
    }

    private static void captureAndBroadcastAudio() {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
            TargetDataLine microphone = AudioSystem.getTargetDataLine(format);
            microphone.open(format);
            microphone.start();

            byte[] buffer = new byte[1024];
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            while (true) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    double dBFS = calculateDBFS(buffer, bytesRead);
                    broadcast(String.valueOf(dBFS));
                }
            }
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    private static double calculateDBFS(byte[] audioData, int bytesRead) {
        double rms = 0.0;
        for (int i = 0; i < bytesRead; i += 2) {
            int sample = ((audioData[i + 1] << 8) | (audioData[i] & 0xff));
            rms += sample * sample;
        }
        rms = rms / (bytesRead / 2);
        rms = Math.sqrt(rms);

        // Calculate dBFS: 20 * log10(rms / MAX)
        double dBFS = 20 * Math.log10(rms / 32768.0);
        return dBFS;
    }
}

Explanation of the Code

	1.	WebSocket Server (@ServerEndpoint):
	•	This WebSocket server listens on the /audio endpoint.
	•	When a new client connects, it adds them to the sessions set.
	•	When a client disconnects, it removes them from sessions.
	•	broadcast(String message) sends a message to all connected clients.
	2.	Audio Capture and dBFS Calculation:
	.	Audio Format: Sample rate set to 48,000 Hz and uses 24-bit samples.
	•	The captureAndBroadcastAudio() method continuously reads data from the microphone.
	•	It uses calculateDBFS(byte[] audioData, int bytesRead) to calculate dBFS.
	•	The computed dBFS value is broadcast to all clients via the WebSocket server.
	3.	calculateDBFS:
	•	This method calculates dBFS by finding the root mean square (RMS) of the samples and converting it to a logarithmic scale.
	•	dBFS is calculated as 20 * log10(rms / MAX), where MAX is the maximum possible value of 24-bit.
	4.	Calculate dBSPL: To calculate dBSPL, we’ll need to interpret the samples as 24-bit integers, compute the RMS, and convert that to SPL (assuming we’re calibrating for a standard microphone sensitivity).
		a 16-bit PCM sample.


Running the Server

	•	Compile and run this server.
	•	Connect to ws://localhost:8080/audio using a WebSocket client to receive real-time dBFS values from the microphone.

Dependencies

If you need to deploy this on a server, you might use a library like Tyrus (WebSocket reference implementation) with Java WebSocket API.

Notes

	1.	The microphone must be available, and the correct permissions should be set to access it.
	2.	The server needs to handle potential scaling issues when many clients are connected