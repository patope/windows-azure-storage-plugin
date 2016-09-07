package com.microsoftopentechnologies.windowsazurestorage;

import java.io.Serializable;

public class AzureBlob implements Serializable {

	private static final long serialVersionUID = -1873484056669542679L;

	private final String containerName;

	private final String blobName;

	private final String blobURL;
	
	public AzureBlob(String containerName, String blobName, String blobURL) {
		this.containerName = containerName;
		this.blobName = blobName;
		this.blobURL = blobURL;
	}

	public String getContainerName() {
		return containerName;
	}

	public String getBlobName() {
		return blobName;
	}
	
	public String getBlobURL() {
		return blobURL;
	}

	@Override
	public String toString() {
		return "AzureBlob [containerName=" + containerName + ",blobName=" + blobName + ", blobURL="
				+ blobURL + "]";
	}
	
	
}
