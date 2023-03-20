package client;

import java.net.*;
import java.io.*;
import java.util.*;

public class xFxClient {

	public static Map<String, Long> convertBytestoMap(byte[] rawData) {
		// Converting byte array to map
		Map<String, Long> fileData = new TreeMap<>();
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(rawData);
			ObjectInputStream ois = new ObjectInputStream(bais);
			fileData = (TreeMap<String, Long>) ois.readObject();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return fileData;
	}

	public static void main(String[] args) throws Exception {

		String command = args[0];

		try (Socket connectionToServer = new Socket("localhost", 80)) {

			// I/O operations

			InputStream in = connectionToServer.getInputStream();
			OutputStream out = connectionToServer.getOutputStream();

			BufferedReader headerReader = new BufferedReader(new InputStreamReader(in));
			BufferedWriter headerWriter = new BufferedWriter(new OutputStreamWriter(out));

			DataInputStream dataIn = new DataInputStream(in);
			DataOutputStream dataOut = new DataOutputStream(out);

			// Preparing Error Messages
			String notConnected = "You're not connected to the right Server!\n";
			String noSuchFileS = "No such file exists on the server!\n";
			String noSuchFileC = "No such file exists on the client!\n";
			String noFiles = "No files to be shared exist on the server!\n";
			String resumeError = "The file you specified is complete!\n";

			if (command.equals("d")) {

				String fileName = args[1];
				File file = new File("ClientShare/" + fileName);
				// If the file exists locally, check if dirty
				if (file.exists()) {
					System.out.println(
							"A local copy of the file: " + fileName + " already exists. Checking for updates...");
					String header = "check " + fileName + " " + file.lastModified() + "\n";
					headerWriter.write(header, 0, header.length());
					headerWriter.flush();

					String response = headerReader.readLine();
					StringTokenizer strk = new StringTokenizer(response, " ");
					String status = strk.nextToken();

					if (status.equals("OK")) {
						System.out.println("The file is up to date!");
					} else if (status.equals("DIRTY")) {
						System.out.println("The file is not up to date, download required!");
						System.out.println("Downloading file from server...");

						int size = Integer.parseInt(strk.nextToken());
						byte[] bytes = new byte[size];
						// Receiving updated bytes from server
						dataIn.readFully(bytes);


						try (FileOutputStream fileOut = new FileOutputStream("ClientShare/" + fileName)) {
							fileOut.write(bytes, 0, size);
						} catch (Exception ex) {
							ex.printStackTrace();
						}

					} else {
						System.out.println(notConnected);
					}
				}

				else {
					System.out.println("The file you specified was not found locally, fetching from server...");
					String header = "download " + fileName + "\n";
					headerWriter.write(header, 0, header.length());
					headerWriter.flush();

					header = headerReader.readLine();

					if (header.equals("NOT FOUND")) {
						System.out.println(noSuchFileS);
					} else {
						StringTokenizer strk = new StringTokenizer(header, " ");

						String status = strk.nextToken();

						if (status.equals("OK")) {

							int size = Integer.parseInt(strk.nextToken());

							byte[] space = new byte[size];


							dataIn.readFully(space);


							try (FileOutputStream fileOut = new FileOutputStream("ClientShare/" + fileName)) {
								fileOut.write(space, 0, size);
								System.out.println("File downloaded successfully!");
							} catch (Exception ex) {
								ex.printStackTrace();
							}
						} else {
							System.out.println(notConnected);
						}
					}

				}

			} else if (command.equals("u")) {

				String fileName = args[1];

				try {
					FileInputStream fileIn = new FileInputStream("ClientShare/" + fileName);
					int fileSize = fileIn.available();
					String header = "upload " + fileName + " " + fileSize + "\n";
					headerWriter.write(header);
					headerWriter.flush();

					byte[] bytes = new byte[fileSize];
					fileIn.read(bytes, 0, fileSize);

					fileIn.close();
					// Sending file bytes to server
					dataOut.write(bytes, 0, fileSize);
					dataOut.flush();

					String response = headerReader.readLine();
					if (response.equals("SUCCESS")) {
						System.out.println("The file: " + fileName + " has been Successfully Uploaded to ServerShare!");
					} else if (response.equals("UPERR")) {
						System.out.println("The file: " + fileName + " could not be uploaded to ServerShare!");
					} else {
						System.out.println(notConnected);
					}
				} catch (Exception ex) {
					System.out.println(noSuchFileC);
					ex.printStackTrace();
				}

			} else if (command.equals("l")) {

				String header = "list" + "\n";
				headerWriter.write(header);
				headerWriter.flush();

				header = headerReader.readLine();
				StringTokenizer strk = new StringTokenizer(header, " ");
				String status = strk.nextToken();
				try {
					if (status.equals("OK")) {
	
						int folderInfoSize = Integer.parseInt(strk.nextToken());
	
						byte[] bytes = new byte[folderInfoSize];
	
						dataIn.read(bytes);
						// Converting bytes received to a map
						Map<String, Long> filesInfoClient = convertBytestoMap(bytes);
	
						for (String name : filesInfoClient.keySet()) {
							System.out.println("The name of the file is: " + name + " with a size of: "
									+ filesInfoClient.get(name) + " bytes.\n");
						}
	
					} else if (status.equals("LISTERR")) {
						System.err.println(noFiles);
					} else {
						System.err.println(notConnected);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}

			} else if (command.equals("r")) {
				String fileName = args[1];

				try {
					FileInputStream fileIn = new FileInputStream("ClientShare/" + fileName);
					// Getting the number of bytes already received on Client side
					int bytesReceived = fileIn.available();

					String header = "resume " + fileName + " " + bytesReceived + " \n";
					headerWriter.write(header);
					headerWriter.flush();

					fileIn.close();

					header = headerReader.readLine();
					StringTokenizer strk = new StringTokenizer(header, " ");
					String status = strk.nextToken();
					
					

					if (status.equals("OK")) {
						// Getting the number of bytes left to receive from Server side
						int bytesLeft = Integer.parseInt(strk.nextToken());

						System.out.println("The file is not complete, we will resume the download!");

						byte[] bytes = new byte[bytesLeft];
						dataIn.readFully(bytes);
						// Appending the bytes received to the file
						try (FileOutputStream fileOut = new FileOutputStream("ClientShare/" + fileName, true)) {
							fileOut.write(bytes, 0, bytesLeft);
						} catch (Exception ex) {
							ex.printStackTrace();
						}

						System.out.println("File Download Resumed successfully");

					} else if (status.equals("COMPLETE")) {
						System.out.println(resumeError);
					} else {
						System.out.println(notConnected);
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				System.out.println(notConnected);
			}
		}
	}
}