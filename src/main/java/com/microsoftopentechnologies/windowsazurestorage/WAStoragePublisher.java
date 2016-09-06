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

import hudson.Launcher;
import hudson.Extension;
import hudson.util.CopyOnWriteList;
import hudson.util.EnumConverter;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;

import com.microsoftopentechnologies.windowsazurestorage.beans.StorageAccountInfo;
import com.microsoftopentechnologies.windowsazurestorage.helper.Utils;

import javax.servlet.ServletException;

import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WAStoragePublisher extends Recorder {

	/** Windows Azure Storage Account Name. */
	private String storageAccName;

	/** Windows Azure storage container name. */
	private String containerName;

	/** Windows Azure storage container access. */
	private boolean cntPubAccess;

	/** Windows Azure storage container cleanup option. */
	private boolean cleanUpContainer;
	
	/** Allowing anonymous access for links generated by jenkins. */
	private boolean allowAnonymousAccess;

	/** If true, uploads artifacts only if the build passed. */
	private boolean uploadArtifactsOnlyIfSuccessful;

	/** If true, build will not be changed to UNSTABLE if archiving returns nothing. */
	private boolean doNotFailIfArchivingReturnsNothing;
	
	/** If true, artifacts will also be uploaded as a zip rollup **/
	private boolean uploadZips;
	
	/** If true, artifacts will not be uploaded as individual files **/
	private boolean doNotUploadIndividualFiles;

	/** Files path. Ant glob syntax. */
	private String filesPath;
	
	/** Files to exclude from archival.  Ant glob syntax */
	private String excludeFilesPath;

	/** File Path prefix */
	private String virtualPath;

	/** Content type for files */
	private String contentType;
	
	public enum UploadType {
		INDIVIDUAL,
		ZIP,
		BOTH,
		INVALID;
	}

	/**
	 *
	 * @param storageAccName
	 * @param filesPath
	 * @param excludeFilesPath
	 * @param containerName
	 * @param cntPubAccess
	 * @param virtualPath
	 * @param cleanUpContainer
	 * @param allowAnonymousAccess
	 * @param uploadArtifactsOnlyIfSuccessful
	 * @param doNotFailIfArchivingReturnsNothing
	 * @param doNotUploadIndividualFiles
	 * @param uploadZips
	 * @param contentType
	 */
	@DataBoundConstructor
	public WAStoragePublisher(final String storageAccName,
			final String filesPath, final String excludeFilesPath, final String containerName,
			final boolean cntPubAccess, final String virtualPath,
			final boolean cleanUpContainer, final boolean allowAnonymousAccess,
			final boolean uploadArtifactsOnlyIfSuccessful,
			final boolean doNotFailIfArchivingReturnsNothing,
			final boolean doNotUploadIndividualFiles,
			final boolean uploadZips,
			final String contentType) {
		super();
		this.storageAccName = storageAccName;
		this.filesPath = filesPath;
		this.excludeFilesPath = excludeFilesPath;
		this.containerName = containerName;
		this.cntPubAccess = cntPubAccess;
		this.virtualPath = virtualPath;
		this.cleanUpContainer = cleanUpContainer;
		this.allowAnonymousAccess = allowAnonymousAccess;
		this.uploadArtifactsOnlyIfSuccessful = uploadArtifactsOnlyIfSuccessful;
		this.doNotFailIfArchivingReturnsNothing = doNotFailIfArchivingReturnsNothing;
		this.doNotUploadIndividualFiles = doNotUploadIndividualFiles;
		this.uploadZips = uploadZips;
		this.contentType = contentType;
	}

	public String getFilesPath() {
		return filesPath;
	}
	
	public String getExcludeFilesPath() {
		return excludeFilesPath;
	}

	public String getContainerName() {
		return containerName;
	}

	public boolean isCntPubAccess() {
		return cntPubAccess;
	}

	public boolean isCleanUpContainer() {
		return cleanUpContainer;
	}
	
	public boolean isAllowAnonymousAccess() {
		return allowAnonymousAccess;
	}
	
	public boolean isDoNotFailIfArchivingReturnsNothing() {
		return doNotFailIfArchivingReturnsNothing;
	}
	
	public boolean isUploadArtifactsOnlyIfSuccessful() {
		return uploadArtifactsOnlyIfSuccessful;
	}
	
	public boolean isUploadZips() {
		return uploadZips;
	}
	
	public boolean isDoNotUploadIndividualFiles() {
		return doNotUploadIndividualFiles;
	}

	public String getContentType() {
		return contentType;
	}

	private UploadType computeArtifactUploadType(final boolean uploadZips, final boolean doNotUploadIndividualFiles) {
		if (uploadZips && !doNotUploadIndividualFiles) {
			return UploadType.BOTH;
		} else if (!uploadZips && !doNotUploadIndividualFiles) {
			return UploadType.INDIVIDUAL;
		} else if (uploadZips && doNotUploadIndividualFiles) {
			return UploadType.ZIP;
		} else {
			return UploadType.INVALID;
		}
	}
	
	public UploadType getArtifactUploadType() {
		return computeArtifactUploadType(this.uploadZips, this.doNotUploadIndividualFiles);
	}

	public String getStorageAccName() {
		return storageAccName;
	}

	public void setStorageAccName(final String storageAccName) {
		this.storageAccName = storageAccName;
	}

	public void setContainerName(final String containerName) {
		this.containerName = containerName;
	}

	public void setCntPubAccess(final boolean cntPubAccess) {
		this.cntPubAccess = cntPubAccess;
	}

	public void setCleanUpContainer(final boolean cleanUpContainer) {
		this.cleanUpContainer = cleanUpContainer;
	}
	
	public void setAllowAnonymousAccess(final boolean allowAnonymousAccess) {
		this.allowAnonymousAccess = allowAnonymousAccess;
	}
	
	public void setDoNotFailIfArchivingReturnsNothing(final boolean doNotFailIfArchivingReturnsNothing) {
		this.doNotFailIfArchivingReturnsNothing = doNotFailIfArchivingReturnsNothing;
	}
	
	public void setUploadArtifactsOnlyIfSuccessful(final boolean uploadArtifactsOnlyIfSuccessful) {
		this.uploadArtifactsOnlyIfSuccessful = uploadArtifactsOnlyIfSuccessful;
	}

	public void setFilesPath(final String filesPath) {
		this.filesPath = filesPath;
	}
	
	public void setExcludeFilesPath(final String excludeFilesPath) {
		this.excludeFilesPath = excludeFilesPath;
	}
	
	public void setUploadZips(final boolean uploadZips) {
		this.uploadZips = uploadZips;
	}
	
	public void setDoNotUploadIndividualFiles() {
		this.doNotUploadIndividualFiles = doNotUploadIndividualFiles;
	}

	public String getVirtualPath() {
		return virtualPath;
	}

	public void setVirtualPath(final String virtualPath) {
		this.virtualPath = virtualPath;
	}

	public WAStorageDescriptor getDescriptor() {
		return (WAStorageDescriptor) super.getDescriptor();
	}
	
	//Defines project actions
	public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
		AzureBlobProjectAction projectAction= new AzureBlobProjectAction(project);
		List<Action> projectActions = new ArrayList<Action>();
		projectActions.add(projectAction);
		
		return Collections.unmodifiableList(projectActions);
	}

	/**
	 * Returns storage account object based on the name selected in job
	 * configuration
	 * 
	 * @return StorageAccount
	 */
	public StorageAccountInfo getStorageAccount() {
		StorageAccountInfo storageAcc = null;
		for (StorageAccountInfo sa : getDescriptor().getStorageAccounts()) {
			if (sa.getStorageAccName().equals(storageAccName)) {
				storageAcc = sa;
				if (storageAcc != null) {
					storageAcc.setBlobEndPointURL(Utils.getBlobEP(
							storageAcc.getBlobEndPointURL()));
				}
				break;
			}
		}
		return storageAcc;
	}

	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {

		// Get storage account and set formatted blob endpoint url.
		StorageAccountInfo strAcc = getStorageAccount();

		// Resolve container name
		String expContainerName = Utils.replaceTokens(build, listener,
				containerName);
		if (expContainerName != null) {
			expContainerName = expContainerName.trim().toLowerCase(
					Locale.ENGLISH);
		}

		// Resolve file path
		String expFP = Utils.replaceTokens(build, listener, filesPath);

		if (expFP != null) {
			expFP = expFP.trim();
		}
		
		// Resolve exclude paths
		String excludeFP = Utils.replaceTokens(build, listener, excludeFilesPath);

		if (excludeFP != null) {
			excludeFP = excludeFP.trim();
		}

		// Resolve virtual path
		String expVP = Utils.replaceTokens(build, listener, virtualPath);
		if (Utils.isNullOrEmpty(expVP)) {
			expVP = null;
		}
		if (!Utils.isNullOrEmpty(expVP) && !expVP.endsWith(Utils.FWD_SLASH)) {
			expVP = expVP.trim() + Utils.FWD_SLASH;
		}

		// Validate input data
		if (!validateData(build, listener, strAcc, expContainerName)) {
			return true; // returning true so that build can continue.
		}

		try {
			List<AzureBlob> individualBlobs = new ArrayList<AzureBlob>();
			List<AzureBlob> archiveBlobs = new ArrayList<AzureBlob>();
			
			int filesUploaded = WAStorageClient.upload(build, listener, strAcc,
					expContainerName, cntPubAccess, cleanUpContainer, expFP,
					expVP, excludeFP, getArtifactUploadType(), individualBlobs, archiveBlobs,
					contentType);

			// Mark build unstable if no files are uploaded and the user
			// doesn't want the build not to fail in that case.
			if (filesUploaded == 0) {
				listener.getLogger().println(
						Messages.WAStoragePublisher_nofiles_uploaded());
				if (!doNotFailIfArchivingReturnsNothing) {
					build.setResult(Result.UNSTABLE);
				}
			} else {
				AzureBlob zipArchiveBlob = null;
				if (getArtifactUploadType() != UploadType.INDIVIDUAL) {
					zipArchiveBlob = archiveBlobs.get(0);
				}
				listener.getLogger().println(Messages.WAStoragePublisher_files_uploaded_count(filesUploaded));
				
				build.getActions().add(new AzureBlobAction(build, strAcc.getStorageAccName(), 
						expContainerName, individualBlobs, zipArchiveBlob, allowAnonymousAccess));
			}
		} catch (Exception e) {
			e.printStackTrace(listener.error(Messages
					.WAStoragePublisher_uploaded_err(strAcc.getStorageAccName())));
			build.setResult(Result.UNSTABLE);
		}
		return true;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.STEP;
	}

	private boolean validateData(AbstractBuild<?, ?> build,
			BuildListener listener, StorageAccountInfo storageAccount,
			String expContainerName) throws IOException, InterruptedException {

		// No need to upload artifacts if build failed and the job is
		// set to not upload on success.
		if ( (build.getResult() == Result.FAILURE || build.getResult() == Result.ABORTED) && uploadArtifactsOnlyIfSuccessful) {
			listener.getLogger().println(
					Messages.WAStoragePublisher_build_failed_err());
			return false;
		}

		if (storageAccount == null) {
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

		// Validate files path
		if (Utils.isNullOrEmpty(filesPath)) {
			listener.getLogger().println(
					Messages.WAStoragePublisher_filepath_err());
			build.setResult(Result.UNSTABLE);
			return false;
		}
		
		if (getArtifactUploadType() == UploadType.INVALID) {
			listener.getLogger().println(
					Messages.WAStoragePublisher_uploadtype_invalid());
			build.setResult(Result.UNSTABLE);
			return false;
		}

		// Check if storage account credentials are valid
		try {
			WAStorageClient.validateStorageAccount(
					storageAccount.getStorageAccName(),
					storageAccount.getStorageAccountKey(),
					storageAccount.getBlobEndPointURL());
		} catch (Exception e) {
			listener.getLogger().println(Messages.Client_SA_val_fail());
			listener.getLogger().println(
					"Storage Account name --->"
							+ storageAccount.getStorageAccName() + "<----");
			listener.getLogger().println(
					"Blob end point url --->"
							+ storageAccount.getBlobEndPointURL() + "<----");
			build.setResult(Result.UNSTABLE);
			return false;
		}
		return true;
	}

	@Extension
	public static final class WAStorageDescriptor extends
			BuildStepDescriptor<Publisher> {

		private final CopyOnWriteList<StorageAccountInfo> storageAccounts = new CopyOnWriteList<StorageAccountInfo>();

		public WAStorageDescriptor() {
			super();
			load();
		}

		public boolean configure(StaplerRequest req, JSONObject formData)
				throws FormException {
			storageAccounts.replaceBy(req.bindParametersToList(
					StorageAccountInfo.class, "was_"));
			save();
			return super.configure(req, formData);
		}

		/**
		 * Validates storage account details.
		 * 
		 * @param was_storageAccName
		 * @param was_storageAccountKey
		 * @param was_blobEndPointURL
		 * @return
		 * @throws IOException
		 * @throws ServletException
		 */
		public FormValidation doCheckAccount(
				@QueryParameter String was_storageAccName,
				@QueryParameter String was_storageAccountKey,
				@QueryParameter String was_blobEndPointURL) throws IOException,
				ServletException {

			if (Utils.isNullOrEmpty(was_storageAccName)) {
				return FormValidation.error(Messages
						.WAStoragePublisher_storage_name_req());
			}

			if (Utils.isNullOrEmpty(was_storageAccountKey)) {
				return FormValidation.error(Messages
						.WAStoragePublisher_storage_key_req());
			}

			try {
				// Get formatted blob end point URL.
				was_blobEndPointURL = Utils.getBlobEP(was_blobEndPointURL);
				WAStorageClient.validateStorageAccount(was_storageAccName,
						was_storageAccountKey, was_blobEndPointURL);
			} catch (Exception e) {
				return FormValidation.error("Error : " + e.getMessage());
			}
			return FormValidation.ok(Messages.WAStoragePublisher_SA_val());
		}

		/**
		 * Checks for valid container name.
		 * 
		 * @param val
		 *            name of the container
		 * @return FormValidation result
		 * @throws IOException
		 * @throws ServletException
		 */
		public FormValidation doCheckName(@QueryParameter String val)
				throws IOException, ServletException {
			if (!Utils.isNullOrEmpty(val)) {
				// Token resolution happens dynamically at runtime , so for
				// basic validations
				// if text contain tokens considering it as valid input.
				if (Utils.containTokens(val)
						|| Utils.validateContainerName(val)) {
					return FormValidation.ok();
				} else {
					return FormValidation.error(Messages
							.WAStoragePublisher_container_name_invalid());
				}
			} else {
				return FormValidation.error(Messages
						.WAStoragePublisher_container_name_req());
			}
		}

		public FormValidation doCheckPath(@QueryParameter String val) {
			if (Utils.isNullOrEmpty(val)) {
				return FormValidation.error(Messages
						.WAStoragePublisher_artifacts_req());
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckBlobName(@QueryParameter String val) {
			if (Utils.isNullOrEmpty(val)) {
				return FormValidation.error(Messages
						.AzureStorageBuilder_blobName_req());
			} else if (!Utils.validateBlobName(val)) {
				return FormValidation.error(Messages
						.AzureStorageBuilder_blobName_invalid());
			} else {
				return FormValidation.ok();
			}

		}

		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public String getDisplayName() {
			return Messages.WAStoragePublisher_displayName();
		}

		public StorageAccountInfo[] getStorageAccounts() {
			return storageAccounts
					.toArray(new StorageAccountInfo[storageAccounts.size()]);
		}

		public StorageAccountInfo getStorageAccount(String name) {

			if (name == null || (name.trim().length() == 0)) {
				return null;
			}

			StorageAccountInfo storageAccountInfo = null;
			StorageAccountInfo[] storageAccounts = getStorageAccounts();

			if (storageAccounts != null) {
				for (StorageAccountInfo sa : storageAccounts) {
					if (sa.getStorageAccName().equals(name)) {
						storageAccountInfo = sa;

						if (storageAccountInfo != null) {
							storageAccountInfo.setBlobEndPointURL(
								Utils.getBlobEP(storageAccountInfo.getBlobEndPointURL())
							);
						}
						break;
					}

				}
			}
			return storageAccountInfo;
		}

		public String getDefaultBlobURL() {
			return Utils.getDefaultBlobURL();
		}

		/*
		 * public List<String> getContainersList(String StorageAccountName) {
		 * try { return WAStorageClient.getContainersList(
		 * getStorageAccount(StorageAccountName), false); } catch (Exception e)
		 * { e.printStackTrace(); return null; } }
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
		 * (containerList != null) { for (String containerName :
		 * getContainersList(storageAccName)) { m.add(containerName); } } return
		 * m; }
		 */
	}
}
