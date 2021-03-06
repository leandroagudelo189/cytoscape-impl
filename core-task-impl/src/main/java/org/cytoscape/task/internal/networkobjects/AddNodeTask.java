package org.cytoscape.task.internal.networkobjects;

/*
 * #%L
 * Cytoscape Core Task Impl (core-task-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2013 The Cytoscape Consortium
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 2.1 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.command.StringToModel;
import org.cytoscape.event.CyEventHelper;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.util.json.CyJSONUtil;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.json.JSONResult;

public class AddNodeTask extends AbstractTask implements ObservableTask {
	CyNode newNode;
	CyEventHelper cyEventHelper;
	CyNetworkViewManager networkViewManager;
	VisualMappingManager visualMappingManager;
	final CyServiceRegistrar serviceRegistrar;

	@Tunable(description="Network", context="nogui", 
	         longDescription=StringToModel.CY_NETWORK_LONG_DESCRIPTION, 
					 exampleStringValue=StringToModel.CY_NETWORK_EXAMPLE_STRING)
	public CyNetwork network = null;

	@Tunable(description="Name of the node to add", 
	         longDescription="The name of the node, which will be assigned to both "+
					                 "the 'name' and 'shared name' columns", 
					 exampleStringValue="Node 1", context="nogui")
	public String name = null;

	public AddNodeTask(VisualMappingManager vmm, CyNetworkViewManager viewManager, CyEventHelper eventHelper,
	                   CyServiceRegistrar registrar) {
		cyEventHelper = eventHelper;
		networkViewManager = viewManager;
		visualMappingManager = vmm;
		this.serviceRegistrar = registrar;
	}

	@Override
	public void run(final TaskMonitor taskMonitor) {
		if (network == null) {
			network = serviceRegistrar.getService(CyApplicationManager.class).getCurrentNetwork();
			if (network == null) {
				taskMonitor.showMessage(TaskMonitor.Level.ERROR, "Network must be specified for add command");
				return;
			}
		}

		newNode = network.addNode();
		if (name != null) {
			network.getRow(newNode).set(CyNetwork.NAME, name);
			network.getRow(newNode).set(CyRootNetwork.SHARED_NAME, name);
		}
		cyEventHelper.flushPayloadEvents();
		if (networkViewManager.viewExists(network)) {
			for (CyNetworkView view: networkViewManager.getNetworkViews(network)) {
				View<CyNode> nodeView = view.getNodeView(newNode);
				VisualStyle style = visualMappingManager.getVisualStyle(view);
				if (style != null) {
					style.apply(network.getRow(newNode), nodeView);
				}
			}
		}
		cyEventHelper.flushPayloadEvents();
		taskMonitor.showMessage(TaskMonitor.Level.INFO, "Added node "+newNode.toString()+" to network");

	}

	public Object getResults(Class type) {
		if (type.equals(CyNode.class)) {
			return newNode;
		} else if (type.equals(String.class)){
			if (newNode == null)
				return "<none>";
			return newNode.toString();
		}  else if (type.equals(JSONResult.class)) {
			JSONResult res = () -> {if (newNode == null) 
				return "{}";
			else {
				CyJSONUtil cyJSONUtil = serviceRegistrar.getService(CyJSONUtil.class);
				return "{\"node\":"+cyJSONUtil.toJson(newNode)+"}";
			}};
			return res;
		}
		return newNode;
	}
	
	public List<Class<?>> getResultClasses() {
		return Arrays.asList(CyNode.class, String.class, JSONResult.class);
	}
}
