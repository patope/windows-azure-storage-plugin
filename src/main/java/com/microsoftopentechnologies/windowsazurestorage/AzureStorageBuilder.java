/*
 Copyright 2014 Microsoft Open Technologies, Inc.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 
 http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package com.microsoftopentechnologies.windowsazurestorage;

import java.io.File;
import java.util.Locale;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import javax.annotation.CheckForNull;

public class AzureStorageBuilder extends Builder {

	private String storageAccName;
	private String containerName;
	private String includeFilesPattern;
	private String excludeFilesPattern;
	private String downloadDirLoc;
	private boolean flattenDirectories;
	private boolean includeArchiveZips;

	@DataBoundConstructor
	public AzureStorageBuilder(String storageAccName, String containerName,
			String includeFilesPattern, String excludeFilesPattern, String downloadDirLoc, boolean flattenDirectories,
			boolean includeArchiveZips) {
		this.storageAccName = storageAccName;
		this.containerName = containerName;
		this.includeFilesPattern = includeFilesPattern;
		this.excludeFilesPattern = excludeFilesPattern;
		this.downloadDirLoc = downloadDirLoc;
		this.flattenDirectories = flattenDirectories;
		this.includeArchiveZips = includeArchiveZips;
	}

	public String getStorageAccName() {
		return storageAccName;
	}

	public void setStorageAccName(String storageAccName) {
		this.storageAccName = storageAccName;
	}

	public String getContainerName() {
		return containerName;
	}

	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}

	public String getIncludeFilesPattern() {
		return includeFilesPattern;
	}

	public void setIncludeFilesPattern(String excludeFilesPattern) {
		this.includeFilesPattern = excludeFilesPattern;
	}
	
	public String getExcludeFilesPattern() {
		return excludeFilesPattern;
	}

	public void setExcludeFilesPattern(String excludeFilesPattern) {
		this.excludeFilesPattern = excludeFilesPattern;
	}

	public String getDownloadDirLoc() {
		return downloadDirLoc;
	}

	public void setDownloadDirLoc(String downloadDirLoc) {
		this.downloadDirLoc = downloadDirLoc;
	}
	
	public boolean isIncludeArchiveZips() {
		return includeArchiveZips;
	}
	
	public void setIncludeArchiveZips(final boolean includeArchiveZips) {
		this.includeArchiveZips = includeArchiveZips;
	}
	
	public boolean isFlattenDirectories() {
		return flattenDirectories;
	}
	
	public void setFlattenDirectories(final boolean flattenDirectories){
		this.flattenDirectories = flattenDirectories;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) {
		StorageAccountInfo strAcc = null;
		try {
			// Get storage account
			strAcc = getDescriptor().getStorageAccount(storageAccName);
			if (strAcc == null) {
				listener.getLogger().println(
						Messages.WAStoragePublisher_storage_account_err());
				build.setResult(Result.UNSTABLE);
				return false;
			}

			// Resolve container name
			String expContainerName = Utils.replaceTokens(build, listener,
					containerName);
			if (expContainerName != null) {
				expContainerName = expContainerName.trim().toLowerCase(
						Locale.ENGLISH);
			}

			// Resolve include patterns
			String expIncludePattern = Utils.replaceTokens(build, listener, includeFilesPattern);
			
			// Resolve exclude patterns
			String expExcludePattern = Utils.replaceTokens(build, listener, excludeFilesPattern);
			
			// If the include is empty, make **/*
			if (Utils.isNullOrEmpty(expIncludePattern)) {
				expIncludePattern = "**/*";
			}
			
			// Exclude archive.zip by default.
			if (!includeArchiveZips) {
				if (expExcludePattern != null) {
					expExcludePattern += ",archive.zip";
				}
				else {
					expExcludePattern = "archive.zip";
				}
			}

			// Resolve download location
			String downloadDir = Utils.replaceTokens(build, listener,
					downloadDirLoc);

			// Validate input data
			if (!validateData(build, listener, strAcc, expContainerName)) {
				return true; // returning true so that build can continue.
			}

			int filesDownloaded = WAStorageClient.download(build, listener,
					strAcc, expContainerName, expIncludePattern, expExcludePattern, 
					downloadDir, flattenDirectories);

			if (filesDownloaded == 0) { // Mark build unstable if no files are
										// downloaded
				listener.getLogger().println(
						Messages.AzureStorageBuilder_nofiles_downloaded());
				build.setResult(Result.UNSTABLE);
			} else {
				listener.getLogger()
						.println(
								Messages.AzureStorageBuilder_files_downloaded_count(filesDownloaded));
			}
		} catch (Exception e) {
			e.printStackTrace(listener.error(Messages
					.AzureStorageBuilder_download_err(strAcc != null ?
							strAcc.getStorageAccName() : storageAccName)));
			build.setResult(Result.UNSTABLE);
		}
		return true;
	}

	private boolean validateData(AbstractBuild build, BuildListener listener,
			StorageAccountInfo strAcc, String expContainerName) {

		// No need to download artifacts if build failed
		if (build.getResult() == Result.FAILURE) {
			listener.getLogger().println(
					Messages.AzureStorageBuilder_build_failed_err());
			return false;
		}

		if (strAcc == null) {
			listener.getLogger().println(
					Messages.WAStoragePublisher_storage_account_err());
			build.setResult(Result.UNSTABLE);
			return false;
		}

		// Validate container name
		if (!Utils.validateContainerName(expContainerName)) {
			listener.getLogger().println(
					Messages.WAStoragePublisher_container_name_err());
			build.setResult(Result.UNSTABLE);
			return false;
		}

		// Check if storage account credentials are valid
		try {
			WAStorageClient.validateStorageAccount(strAcc.getStorageAccName(),
					strAcc.getStorageAccountKey(), strAcc.getBlobEndPointURL());
		} catch (Exception e) {
			listener.getLogger().println(Messages.Client_SA_val_fail());
			listener.getLogger().println(strAcc.getStorageAccName());
			listener.getLogger().println(strAcc.getBlobEndPointURL());
			build.setResult(Result.UNSTABLE);
			return false;
		}
		return true;
	}

	public AzureStorageBuilderDesc getDescriptor() {
		// see Descriptor javadoc for more about what a descriptor is.
		return (AzureStorageBuilderDesc) super.getDescriptor();
	}

	@Extension
	public static final class AzureStorageBuilderDesc extends
			Descriptor<Builder> {

		public boolean isApplicable(
				@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
			return true;
		}

		public AzureStorageBuilderDesc() {
			super();
			load();
		}

		// Can be used in future for dynamic display in UI
		/*
		 * private List<String> getContainersList(String StorageAccountName) {
		 * try { return WAStorageClient.getContainersList(
		 * getStorageAccount(StorageAccountName), false); } catch (Exception e)
		 * { e.printStackTrace(); return null; } }
		 * 
		 * private List<String> getBlobsList(String StorageAccountName, String
		 * containerName) { try { return WAStorageClient.getContainerBlobList(
		 * getStorageAccount(StorageAccountName), containerName); } catch
		 * (Exception e) { e.printStackTrace(); return null; } }
		 */

		public ListBoxModel doFillStorageAccNameItems() {
			ListBoxModel m = new ListBoxModel();
			StorageAccountInfo[] StorageAccounts = getStorageAccounts();

			if (StorageAccounts != null) {
				for (StorageAccountInfo storageAccount : StorageAccounts) {
					m.add(storageAccount.getStorageAccName());
				}
			}
			return m;
		}

		/*
		 * public ComboBoxModel doFillContainerNameItems(
		 * 
		 * @QueryParameter String storageAccName) { ComboBoxModel m = new
		 * ComboBoxModel();
		 * 
		 * List<String> containerList = getContainersList(storageAccName); if
		 * (containerList != null) { m.addAll(containerList); } return m; }
		 * 
		 * public ComboBoxModel doFillBlobNameItems(
		 * 
		 * @QueryParameter String storageAccName,
		 * 
		 * @QueryParameter String containerName) { ComboBoxModel m = new
		 * ComboBoxModel();
		 * 
		 * List<String> blobList = getBlobsList(storageAccName, containerName);
		 * if (blobList != null) { m.addAll(blobList); } return m; }
		 * 
		 * public AutoCompletionCandidates
		 * doAutoCompleteBlobName(@QueryParameter String storageAccName,
		 * 
		 * @QueryParameter String containerName) { List<String> blobList =
		 * getBlobsList(storageAccName, containerName); AutoCompletionCandidates
		 * autoCand = null;
		 * 
		 * if (blobList != null ) { autoCand = new AutoCompletionCandidates();
		 * autoCand.add(blobList.toArray(new String[blobList.size()])); } return
		 * autoCand; }
		 */

		/* public FormValidation doCheckIsDirectory(@QueryParameter String val) {
			// If null or if file does not exists don't display any validation
			// error.
			File downloadDir = new File(val);
			if (Utils.isNullOrEmpty(val) || !downloadDir.exists()) {
				return FormValidation.ok();
			} else if (downloadDir.exists() && !downloadDir.isDirectory()) {
				return FormValidation.error(Messages
						.AzureStorageBuilder_downloadDir_invalid());
			} else {
				return FormValidation.ok();
			}
		} */

		public boolean configure(StaplerRequest req, JSONObject formData)
				throws FormException {
			save();
			return super.configure(req, formData);
		}

		public String getDisplayName() {
			return Messages.AzureStorageBuilder_displayName();
		}

		@CheckForNull
		public StorageAccountInfo[] getStorageAccounts() {
			Jenkins jenkins = Jenkins.getInstance();
			if (jenkins == null) {
				return null;
			}
			WAStoragePublisher.WAStorageDescriptor publisherDescriptor = jenkins.getDescriptorByType(
							WAStoragePublisher.WAStorageDescriptor.class);

			return publisherDescriptor.getStorageAccounts();
		}

		/**
		 * Returns storage account object
		 *
		 * @param storageAccountName
		 * @return StorageAccount
		 */
		public StorageAccountInfo getStorageAccount(String storageAccountName) {
			if ((storageAccountName == null)
					|| (storageAccountName.trim().length() == 0)) {
				return null;
			}

			StorageAccountInfo storageAcc = null;
			StorageAccountInfo[] StorageAccounts = getStorageAccounts();

			if (StorageAccounts != null) {
				for (StorageAccountInfo sa : StorageAccounts) {
					if (sa.getStorageAccName().equals(storageAccountName)) {
						storageAcc = sa;

						storageAcc.setBlobEndPointURL(Utils.getBlobEP(
								storageAcc.getBlobEndPointURL()));
						break;
					}
				}
			}
			return storageAcc;
		}
	}
}
