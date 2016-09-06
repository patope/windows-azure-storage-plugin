package com.microsoftopentechnologies.windowsazurestorage.pipeline;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;

@ExportedBean
public class AzureStoragePublishStep extends AbstractStepImpl {

    public static class Execution extends AbstractStepExecutionImpl {

        @Override
        public boolean start() throws Exception {
            return false;
        }

        @Override
        public void stop(@Nonnull Throwable cause) throws Exception {

        }
    }


    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getDisplayName() {
            return "Publish artifacts to Azure Storage";
        }

        @Override
        public String getFunctionName() {
            return "azureStoragePublish";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }
}
