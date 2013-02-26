package com.joelj.jenkins.eztemplates.utils;

import hudson.XmlFile;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Items;
import hudson.util.AtomicFileWriter;
import hudson.util.IOException2;
import jenkins.model.Jenkins;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

/**
 * User: Joel Johnson
 * Date: 2/25/13
 * Time: 11:49 PM
 */
public class ProjectUtils {
	public static AbstractProject findProject(String name) {
		List<AbstractProject> projects = Jenkins.getInstance().getAllItems(AbstractProject.class);
		for (AbstractProject project : projects) {
			if(name.equals(project.getName())) {
				return project;
			}
		}
		return null;
	}

	/**
	 * Silently saves the project without triggering any save events.
	 * Use this method to save a project from within an Update event handler.
	 */
	public static void silentSave(AbstractProject project) throws IOException {
		project.getConfigFile().write(project);
	}

	/**
	 * Copied from {@link AbstractProject#updateByXml(javax.xml.transform.Source)}, removing the save event and
	 * 	returning the project after the update.
	 */
	@SuppressWarnings("unchecked")
	public static AbstractProject updateProjectWithXmlSource(AbstractProject project, Source source) throws IOException {
		String projectName = project.getName();

		XmlFile configXmlFile = project.getConfigFile();
		AtomicFileWriter out = new AtomicFileWriter(configXmlFile.getFile());
		try {
			try {
				// this allows us to use UTF-8 for storing data,
				// plus it checks any well-formedness issue in the submitted
				// data
				Transformer t = TransformerFactory.newInstance()
						.newTransformer();
				t.transform(source,
						new StreamResult(out));
				out.close();
			} catch (TransformerException e) {
				throw new IOException2("Failed to persist configuration.xml", e);
			}

			// try to reflect the changes by reloading
			new XmlFile(Items.XSTREAM, out.getTemporaryFile()).unmarshal(project);
			project.onLoad(project.getParent(), project.getRootDir().getName());
			Jenkins.getInstance().rebuildDependencyGraph();

			// if everything went well, commit this new version
			out.commit();
			return ProjectUtils.findProject(projectName);
		} finally {
			out.abort(); // don't leave anything behind
		}
	}

	/**
	 * The abstract project returns an unmodifiable collection.
	 */
	@SuppressWarnings("unchecked")
	public static List<Action> getActions(AbstractProject project) {
		try {
			Field actions = Actionable.class.getField("actions");
			actions.setAccessible(true);
			return (List<Action>) actions.get(project);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
