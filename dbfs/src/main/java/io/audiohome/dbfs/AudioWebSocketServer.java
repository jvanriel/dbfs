package io.audiohome.dbfs;

import javax.sound.sampled.*;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import org.glassfish.tyrus.server.Server;

// import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;


@ServerEndpoint("/ws")
public class AudioWebSocketServer {
	private static Set<Session> sessions = new CopyOnWriteArraySet<>();

	@OnOpen
	public void onOpen(Session session) {
		System.out.println("Session: " + session.getId() + " open");
		sessions.add(session);
	}

	@OnClose
	public void onClose(Session session) {
		System.out.println("Session: " + session.getId() + " closed");
		sessions.remove(session);
	}

	@OnError
	public void onError(Session session, Throwable throwable) {
		System.out.println("Session: " + session.getId() + " error");
		throwable.printStackTrace();
	}

	public static void broadcast(String message) {
		System.out.println("Broadcasting: " + message);
		for (Session session : sessions) {
			try {
				session.getBasicRemote().sendText(message);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		// Set up the server on port 8080
    // Start the WebSocket server
	  Server server = new Server("localhost", 8080, "/", null, AudioWebSocketServer.class);
		//Server server = new Server("localhost", 8080, "/ws", null, AudioWebSocketServer.class);

		try {
			server.start();
			System.out.println("WebSocket server started at ws://localhost:8080/ws");

			// Start capturing audio and broadcasting dBFS and dBSPL values
			new Thread(AudioWebSocketServer::captureAndBroadcastAudio).start();

			// Keep server running
			System.in.read();  // Press Enter to stop the server

		} catch (Exception e) {
				e.printStackTrace();
		} finally {
				server.stop();
		}

	}

	private static TargetDataLine getMicrophoneByName(String mixerName, AudioFormat format) {
		for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
				if (mixerInfo.getName().contains(mixerName)) {
						Mixer mixer = AudioSystem.getMixer(mixerInfo);
						try {
								DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
								if (mixer.isLineSupported(dataLineInfo)) {
										return (TargetDataLine) mixer.getLine(dataLineInfo);
								}
						} catch (LineUnavailableException e) {
								e.printStackTrace();
						}
				}
		}
		return null;
	}

	private static void captureAndBroadcastAudio() {
		try {
			String mixerName = "E.A.R.S Gain: 18dB";
			AudioFormat format = new AudioFormat(48000, 24, 2, true, true);
			TargetDataLine microphone = getMicrophoneByName(mixerName, format);
			if (microphone == null) {
				System.out.println("Microphone named '" + mixerName + "' not found.");
				return;
			}
			microphone.open(format);
			microphone.start();

			byte[] buffer = new byte[4092]; // integral number of frames for 24-bit 
			while (true) {
				int bytesRead = microphone.read(buffer, 0, buffer.length);
				if (bytesRead > 0) {
					double dBFS = calculateDBFS(buffer, bytesRead);
					if (Double.isNaN(dBFS)) {
						continue;
					} else {
						broadcast("{\"name\": \"dBFS\", \"value\": " + dBFS+"}");
					}
					double dBSPL = calculateDBSPL(buffer, bytesRead);
					if (Double.isNaN(dBSPL)) {
						continue;
					} else {
						broadcast("{\"name\": \"dBSPL\", \"value\": " + dBSPL+"}");
					}
				}
			}
		} catch (LineUnavailableException e) {
			e.printStackTrace();
		}
	}

	private static double calculateRMSLevel(byte[] buffer, int bytesRead) {
		long sum = 0;
		int sampleCount = bytesRead / 4;  // 4 bytes per 32-bit sample
		// Convert byte pairs to 32-bit samples and calculate sum of squares
		for (int i = 0; i < bytesRead; i += 4) {
				int sample = ((buffer[i] << 24) | ((buffer[i + 1] & 0xFF) << 16) | ((buffer[i + 2] & 0xFF) << 8) | (buffer[i + 3] & 0xFF));  // Big-endian
				sum += sample * sample;
		}
		return Math.sqrt(sum / (double) sampleCount);
	}

	private static double calculateDBFS(byte[] audioData, int bytesRead) {
		double rms = calculateRMSLevel(audioData, bytesRead);
		// Calculate dBFS: 20 * log10(rms / MAX)
		return 20 * Math.log10(rms / 8388608.0); // 24-bit max value is 2^23 = 8388608
	}

	private static double calculateDBSPL(byte[] audioData, int bytesRead) {
		double rms = calculateRMSLevel(audioData, bytesRead);
		// Assume calibration constant (mic sensitivity) - adjust as needed
		double referenceSPL = 94.0; // Reference SPL at 1 Pascal
		double micSensitivity = 0.1; // Adjust this based on microphone (e.g., V/Pa)
		// Calculate dBSPL
		return 20 * Math.log10(rms / (8388608.0 * micSensitivity)) + referenceSPL;
	}
}