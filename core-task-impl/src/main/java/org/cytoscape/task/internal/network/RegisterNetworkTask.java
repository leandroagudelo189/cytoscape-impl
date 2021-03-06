package org.cytoscape.task.internal.network;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.command.StringToModel;
import org.cytoscape.group.CyGroup;
import org.cytoscape.group.CyGroupFactory;
import org.cytoscape.group.CyGroupManager;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNetworkTableManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.VirtualColumnInfo;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CyRootNetworkManager;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.task.internal.utils.DataUtils;
import org.cytoscape.task.internal.view.CopyExistingViewTask;
import org.cytoscape.util.json.CyJSONUtil;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.json.JSONResult;

/*
 * #%L
 * Cytoscape Core Task Impl (core-task-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2016 The Cytoscape Consortium
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

/**
 * Registers a new Network and/or Network View and set them as current.
 */
public class RegisterNetworkTask extends AbstractTask implements ObservableTask {

	private final List<CyNetwork> networks;
	private final List<CyNetworkView> views;
	private final VisualStyle style;
	private final VisualMappingManager vmm;
	private final CyApplicationManager appMgr;
	private final CyNetworkManager networkManager;
	private final CyNetworkViewManager networkViewManager;
	private final boolean singleton;
	
	public RegisterNetworkTask(final CyNetwork network,
	                           final CyNetworkManager netmgr,
	                           final VisualMappingManager vmm, 
	                           final CyApplicationManager appMgr, 
	                           final CyNetworkViewManager networkViewManager){
		this.networks = Collections.singletonList(network);
		this.views = null;
		this.style = null;
		this.vmm = vmm;
		this.networkManager = netmgr;
		this.networkViewManager = networkViewManager;
		this.appMgr = appMgr;
		this.singleton = true;
	}

	public RegisterNetworkTask(final CyNetworkView view, 
	                           final VisualStyle style,
	                           final CyNetworkManager netmgr,
	                           final VisualMappingManager vmm, 
	                           final CyApplicationManager appMgr, 
	                           final CyNetworkViewManager networkViewManager){
		this.networks = Collections.singletonList(view.getModel());
		this.views = Collections.singletonList(view);
		this.style = style;
		this.vmm = vmm;
		this.networkManager = netmgr;
		this.networkViewManager = networkViewManager;
		this.appMgr = appMgr;
		this.singleton = true;
	}
	
	public RegisterNetworkTask(final List<CyNetworkView> views, 
	                           final VisualStyle style,
	                           final CyNetworkManager netmgr,
	                           final VisualMappingManager vmm, 
	                           final CyApplicationManager appMgr, 
	                           final CyNetworkViewManager networkViewManager){
		this.views = views;
		this.networks = new ArrayList<CyNetwork>();
		for (CyNetworkView view: views) {
			networks.add(view.getModel());
		}
		this.style = style;
		this.vmm = vmm;
		this.networkManager = netmgr;
		this.networkViewManager = networkViewManager;
		this.appMgr = appMgr;
		this.singleton = false;
	}
	
	@Override
	public void run(TaskMonitor tm) throws Exception {
		tm.setProgress(0.0);
		
		for (CyNetwork network: networks) {
			if (!networkManager.networkExists(network.getSUID()))
				networkManager.addNetwork(network, false);
		}
		
		tm.setProgress(0.1);
		
		if (views != null ) {
			for (CyNetworkView view: views) {
				if (view != null) {
					networkViewManager.addNetworkView(view, false);
					tm.setProgress(0.2);
					
					if (style != null) {
						vmm.setVisualStyle(style, view);
						tm.setProgress(0.8);
					}
					view.updateView();
				}
			}
		}
		
		if (views != null) {
			appMgr.setCurrentNetworkView(views.get(0));
			appMgr.setSelectedNetworkViews(views);
			tm.setProgress(0.9);
		} else {
			appMgr.setCurrentNetwork(networks.get(0));
		}
		
		tm.setProgress(1.0);
	}

	@Override
	public Object getResults(Class type) {
		if (type.equals(List.class)) {
			return views;
		} else if (type.equals(String.class)) {
			if (networks == null) return null;
			String res = "";
			if (views != null && views.size() > 0) {
				res += "Views:\n";
				for (CyNetworkView view: views) {
					res += "    "+DataUtils.getViewTitle(view) + " (SUID: " + view.getSUID() + ")" + "\n";
				}
			}
			res += "Networks:\n";
			for (CyNetwork network: networks) {
				res += "    "+DataUtils.getNetworkName(network) + " (SUID: " + network.getSUID() + ")" + "\n";
			}
			return res;
		} else if (type.equals(CyNetwork.class))
			return networks.get(0);
		else if (type.equals(CyNetworkView.class)) {
			if (views != null && views.size() > 0)
				return views.get(0);
			else
				return null;
		} else if (type.equals(JSONResult.class)) {
			JSONResult res = () -> {if (networks == null) 
				return "{}";
			else if (singleton) {
				// Special case single network
				CyNetwork network = networks.get(0);
				CyNetworkView view = null;
				if (views.size() == 1)
					view = views.get(0);
				String strRes = jsonNetView(network, view);
				return jsonNetView(network, view);
			} else {
				String strRes = "[";
				Set<CyNetwork> viewNets = new HashSet<>();
				boolean first = true;
				for (CyNetworkView view: views) {
					CyNetwork net = view.getModel();
					viewNets.add(net);
					if (!first) 
						strRes += ",";
					else
						first = false;
					strRes += jsonNetView(net, view);
				}
				for (CyNetwork net: networks) {
					if (!viewNets.contains(net)) {
						strRes += jsonNetView(net, null);
					}
				}
				strRes+="]";
				return strRes;
			}};
			return res;
		}
		return null;
	}

	@Override
	public List<Class<?>> getResultClasses() {
		return Arrays.asList(List.class, CyNetworkView.class, CyNetwork.class, String.class, JSONResult.class);
	}

	private String jsonNetView(CyNetwork net, CyNetworkView view) {
		if (net == null) return null;

		if (view == null)
			return "{\"network\":"+net.getSUID()+"}";

		return "{\"network\":"+net.getSUID()+",\"view\":"+view.getSUID()+"}";
	}
}
