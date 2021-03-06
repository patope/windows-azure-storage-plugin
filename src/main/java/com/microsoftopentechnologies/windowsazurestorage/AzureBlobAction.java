package com.microsoftopentechnologies.windowsazurestorage;

import com.microsoftopentechnologies.windowsazurestorage.WAStoragePublisher.WAStorageDescriptor;
import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.security.SecurityMode;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;


public class AzureBlobAction implements RunAction2 {
	private final AbstractBuild build;
	private final String storageAccountName;
	private final boolean allowAnonymousAccess;
	private final List<AzureBlob> individualBlobs;

	public AzureBlobAction(AbstractBuild build, String storageAccountName,
			List<AzureBlob> individualBlobs,
			boolean allowAnonymousAccess) {
		this.build = build;
		this.storageAccountName = storageAccountName;
		this.individualBlobs = individualBlobs;
		this.allowAnonymousAccess = allowAnonymousAccess;
	}
	
	public String getDisplayName() {
		return "Azure Artifacts";
	}

	public String getIconFileName() {
		return "/plugin/windows-azure-storage/images/24x24/Azure.png";
	}

	public String getUrlName() {
		return "Azure";
	}
	
	public void onAttached(Run arg0) {
	}

	public void onBuildComplete() {
	}

	@Override
	public void onLoad(Run<?, ?> r) {

	}
	
	public AbstractBuild<?,?> getBuild() {
	      return build;
	}
	
	public String getStorageAccountName() {
		return storageAccountName;
	}
	
	public List<AzureBlob> getIndividualBlobs() {
		return individualBlobs;
	}
	
	public boolean getAllowAnonymousAccess() {
		return allowAnonymousAccess;
	}

	@CheckForNull
	private WAStoragePublisher.WAStorageDescriptor getWAStorageDescriptor() {
		Jenkins jenkins = Jenkins.getInstance();
		if (jenkins == null) {
			return null;
		}
		WAStoragePublisher.WAStorageDescriptor desc = jenkins.getDescriptorByType(WAStoragePublisher.WAStorageDescriptor.class);
		return desc;
	}
	
	private String getSASURL(String containerName, StorageAccountInfo accountInfo) throws Exception {
		try {
			return WAStorageClient.generateSASURL(accountInfo.getStorageAccName(), accountInfo.getStorageAccountKey(), 
					containerName, accountInfo.getBlobEndPointURL());
		} catch (Exception e) {
			//TODO: handle this in a better way
			e.printStackTrace();
			return "";
		}
	}
	
	public void doProcessDownloadRequest(final StaplerRequest request, final StaplerResponse response) throws IOException, ServletException {
		WAStorageDescriptor storageDesc = getWAStorageDescriptor();
		if (storageDesc == null) {
			return;
		}
		StorageAccountInfo accountInfo  = storageDesc.getStorageAccount(storageAccountName);
		
		if (accountInfo == null) {
			response.sendError(500, "Azure Storage account global configuration is missing");
			return;
		}

		if (Jenkins.getActiveInstance().getSecurity() != SecurityMode.UNSECURED
				&& !allowAnonymousAccess && isAnonymousAccess(Jenkins.getAuthentication())) {
			String url = request.getOriginalRequestURI();
			response.sendRedirect("/login?from=" + url);
			return;
		}
		
		String queryPath = request.getRestOfPath();
		
		if (queryPath == null) {
	          return;
	    }
		
		String blobName = queryPath.substring(1);
		
		// Check the archive blob if it is non-null
		for (AzureBlob blob : individualBlobs) {
			if (blob.getBlobName().equals(blobName)) {
				try {
					response.sendRedirect2(blob.getBlobURL()+"?"+getSASURL(blob.getContainerName(), accountInfo));
				} catch(Exception e) {
					response.sendError(500, "Error occurred while downloading artifact "+e.getMessage());
				}
				return;
			}
		}
		
		response.sendError(404, "Azure artifact is not available");
	}
	
	public boolean isAnonymousAccess(Authentication auth) {
		if (auth != null && auth.getName() != null && "anonymous".equals(auth.getName())) {
			return true;
		}
		return false;
	}

}
