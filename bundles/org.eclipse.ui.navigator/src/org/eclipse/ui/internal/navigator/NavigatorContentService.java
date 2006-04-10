/*******************************************************************************
 * Copyright (c) 2003, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.navigator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.WeakHashMap;

import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.internal.navigator.dnd.NavigatorDnDService;
import org.eclipse.ui.internal.navigator.extensions.ExtensionPriorityComparator;
import org.eclipse.ui.internal.navigator.extensions.NavigatorContentDescriptor;
import org.eclipse.ui.internal.navigator.extensions.NavigatorContentDescriptorManager;
import org.eclipse.ui.internal.navigator.extensions.NavigatorContentExtension;
import org.eclipse.ui.internal.navigator.extensions.NavigatorViewerDescriptor;
import org.eclipse.ui.internal.navigator.extensions.NavigatorViewerDescriptorManager;
import org.eclipse.ui.internal.navigator.extensions.StructuredViewerManager;
import org.eclipse.ui.internal.navigator.sorters.NavigatorSorterService;
import org.eclipse.ui.navigator.IDescriptionProvider;
import org.eclipse.ui.navigator.IExtensionActivationListener;
import org.eclipse.ui.navigator.IExtensionStateModel;
import org.eclipse.ui.navigator.IMementoAware;
import org.eclipse.ui.navigator.INavigatorActivationService;
import org.eclipse.ui.navigator.INavigatorContentDescriptor;
import org.eclipse.ui.navigator.INavigatorContentExtension;
import org.eclipse.ui.navigator.INavigatorContentService;
import org.eclipse.ui.navigator.INavigatorContentServiceListener;
import org.eclipse.ui.navigator.INavigatorDnDService;
import org.eclipse.ui.navigator.INavigatorFilterService;
import org.eclipse.ui.navigator.INavigatorPipelineService;
import org.eclipse.ui.navigator.INavigatorSaveablesService;
import org.eclipse.ui.navigator.INavigatorSorterService;
import org.eclipse.ui.navigator.INavigatorViewerDescriptor;

/**
 * <p>
 * Provides centralized access to the information provided by
 * NavigatorContentExtensions. Can be instantiated as needed, but should be
 * cached for active viewers. Information specific to a given viewer will be
 * cached by the NavigatorContentService, not including ContentProviders and
 * Label Providers created by {@link #createCommonContentProvider()}and
 * {@link #createCommonLabelProvider()}respectively.
 * </p>
 * 
 * <p>
 * The following class is experimental until fully documented.
 * </p>
 */
public class NavigatorContentService implements IExtensionActivationListener,
		IMementoAware, INavigatorContentService {

	private static final NavigatorContentDescriptorManager CONTENT_DESCRIPTOR_REGISTRY = NavigatorContentDescriptorManager
			.getInstance();

	private static final NavigatorViewerDescriptorManager VIEWER_DESCRIPTOR_REGISTRY = NavigatorViewerDescriptorManager
			.getInstance();

	private static final ITreeContentProvider[] NO_CONTENT_PROVIDERS = new ITreeContentProvider[0];

	private static final ILabelProvider[] NO_LABEL_PROVIDERS = new ILabelProvider[0];

	private static final INavigatorContentDescriptor[] NO_DESCRIPTORS = new INavigatorContentDescriptor[0];

	private static final String[] NO_EXTENSION_IDS = new String[0];

	private final NavigatorViewerDescriptor viewerDescriptor;

	private final List listeners = new ArrayList();

	/*
	 * A map of (String-based-Navigator-Content-Extension-IDs,
	 * NavigatorContentExtension-objects)-pairs
	 */
	private final Map contentExtensions = new HashMap();

	private StructuredViewerManager structuredViewerManager;

	private ITreeContentProvider[] rootContentProviders;

	private WeakHashMap contributionMemory;

	private ITreeContentProvider contentProvider;

	private ILabelProvider labelProvider;

	private final VisibilityAssistant assistant;

	private NavigatorFilterService navigatorFilterService;

	private INavigatorSorterService navigatorSorterService;

	private INavigatorPipelineService navigatorPipelineService;

	private INavigatorDnDService navigatorDnDService;

	private INavigatorActivationService navigatorActivationService;

	private IDescriptionProvider descriptionProvider;

	private boolean contentProviderInitialized;

	private boolean labelProviderInitialized;

	private NavigatorSaveablesService navigatorSaveablesService;

	/**
	 * @param aViewerId
	 *            The viewer id for this content service; normally from the
	 *            <b>org.eclipse.ui.views</b> extension.
	 */
	public NavigatorContentService(String aViewerId) {
		super();
		aViewerId = aViewerId != null ? aViewerId : ""; //$NON-NLS-1$
		viewerDescriptor = VIEWER_DESCRIPTOR_REGISTRY
				.getNavigatorViewerDescriptor(aViewerId);
		assistant = new VisibilityAssistant(viewerDescriptor, getActivationService());
		getActivationService().addExtensionActivationListener(this);
	}

	/**
	 * @param aViewerId
	 *            The viewer id for this content service; normally from the
	 *            <b>org.eclipse.ui.views</b> extension.
	 * @param aViewer
	 *            The viewer that this content service will be associated with.
	 */
	public NavigatorContentService(String aViewerId, StructuredViewer aViewer) {
		this(aViewerId);
		structuredViewerManager = new StructuredViewerManager(aViewer);
	}

	public String[] getVisibleExtensionIds() {

		List visibleExtensionIds = new ArrayList();

		NavigatorContentDescriptor[] descriptors = CONTENT_DESCRIPTOR_REGISTRY
				.getAllContentDescriptors();
		for (int i = 0; i < descriptors.length; i++) {
			if (assistant.isVisible(descriptors[i].getId())) {
				visibleExtensionIds.add(descriptors[i].getId());
			}
		}
		if (visibleExtensionIds.isEmpty()) {
			return NO_EXTENSION_IDS;
		}
		return (String[]) visibleExtensionIds
				.toArray(new String[visibleExtensionIds.size()]);

	}

	public INavigatorContentDescriptor[] getVisibleExtensions() {
		List visibleDescriptors = new ArrayList();

		NavigatorContentDescriptor[] descriptors = CONTENT_DESCRIPTOR_REGISTRY
				.getAllContentDescriptors();
		for (int i = 0; i < descriptors.length; i++) {
			if (assistant.isVisible(descriptors[i].getId())) {
				visibleDescriptors.add(descriptors[i]);
			}
		}
		if (visibleDescriptors.isEmpty()) {
			return NO_DESCRIPTORS;
		}
		return (INavigatorContentDescriptor[]) visibleDescriptors
				.toArray(new INavigatorContentDescriptor[visibleDescriptors
						.size()]);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.navigator.INavigatorContentService#bindExtensions(java.lang.String[],
	 *      boolean)
	 */
	public INavigatorContentDescriptor[] bindExtensions(String[] extensionIds,
			boolean isRoot) {
		if (extensionIds == null || extensionIds.length == 0) {
			return NO_DESCRIPTORS;
		}

		for (int i = 0; i < extensionIds.length; i++) {
			assistant.bindExtensions(extensionIds, isRoot);
		}
		Set boundDescriptors = new HashSet();
		INavigatorContentDescriptor descriptor;
		for (int i = 0; i < extensionIds.length; i++) {
			descriptor = CONTENT_DESCRIPTOR_REGISTRY
					.getContentDescriptor(extensionIds[i]);
			if (descriptor != null) {
				boundDescriptors.add(descriptor);
			}
		}

		if (boundDescriptors.size() == 0) {
			return NO_DESCRIPTORS;
		}
		return (INavigatorContentDescriptor[]) boundDescriptors
				.toArray(new INavigatorContentDescriptor[boundDescriptors
						.size()]);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.navigator.INavigatorContentService#createCommonContentProvider()
	 */
	public ITreeContentProvider createCommonContentProvider() {
		if (contentProviderInitialized) {
			return contentProvider;
		}
		synchronized (this) {
			if (contentProvider == null) {
				contentProvider = new NavigatorContentServiceContentProvider(
						this);
			}
			contentProviderInitialized = true;
		}
		return contentProvider;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.navigator.INavigatorContentService#createCommonLabelProvider()
	 */
	public ILabelProvider createCommonLabelProvider() {
		if (labelProviderInitialized) {
			return labelProvider;
		}
		synchronized (this) {
			if (labelProvider == null) {
				labelProvider = new NavigatorContentServiceLabelProvider(this);
			}
			labelProviderInitialized = true;
		}
		return labelProvider;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.navigator.INavigatorContentService#createCommonDescriptionProvider()
	 */
	public IDescriptionProvider createCommonDescriptionProvider() {
		if (descriptionProvider != null) {
			return descriptionProvider;
		}
		synchronized (this) {
			if (descriptionProvider == null) {
				descriptionProvider = new NavigatorContentServiceDescriptionProvider(
						this);
			}
		}
		return descriptionProvider;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.navigator.INavigatorContentService#dispose()
	 */
	public void dispose() {
		for (Iterator contentItr = contentExtensions.values().iterator(); contentItr
				.hasNext();) {
			((NavigatorContentExtension) contentItr.next()).dispose();
		}
		getActivationService().removeExtensionActivationListener(this);
		assistant.dispose();
	}

	protected void updateService(Viewer aViewer, Object anOldInput,
			Object aNewInput) {

		synchronized (this) {

			if (structuredViewerManager == null) {
				structuredViewerManager = new StructuredViewerManager(aViewer);
				structuredViewerManager.inputChanged(anOldInput, aNewInput);
			} else {
				structuredViewerManager.inputChanged(aViewer, anOldInput,
						aNewInput);
			}

			for (Iterator contentItr = contentExtensions.values().iterator(); contentItr
					.hasNext();) {
				structuredViewerManager
						.initialize(((NavigatorContentExtension) contentItr
								.next()).getContentProvider());
			}

			rootContentProviders = extractContentProviders(findRootContentExtensions(aNewInput));
		}
	}

	public IExtensionStateModel findStateModel(String anExtensionId) {
		if (anExtensionId == null) {
			return null;
		}
		INavigatorContentDescriptor desc = CONTENT_DESCRIPTOR_REGISTRY
				.getContentDescriptor(anExtensionId);
		if (desc == null) {
			return null;
		}
		INavigatorContentExtension ext = getExtension(desc);
		if (ext == null) {
			return null;
		}
		return ext.getStateModel();
	}

	/**
	 * Return a set of content providers that could provide a parent for the
	 * given element. These content extensions are determined by consulting the
	 * <b>possibleChildren</b> expression in the <b>navigatorContent</b>
	 * extension.
	 * 
	 * <p>
	 * Clients that wish to tap into the link with editor support must describe
	 * all of their possible children in their <b>possibleChildren</b>
	 * expressions.
	 * </p>
	 * 
	 * @param anElement
	 *            An element from the tree (generally from a setSelection()
	 *            method).
	 * @return The set of content providers that may be able to provide a
	 *         parent.
	 */
	public ITreeContentProvider[] findParentContentProviders(Object anElement) {
		return extractContentProviders(findContentExtensionsWithPossibleChild(anElement));
	}

	/**
	 * <p>
	 * Return all of the content providers that are relevant for the viewer. The
	 * viewer is determined by the ID used to create the
	 * INavigatorContentService ({@link #getViewerId() }). See
	 * {@link #createCommonContentProvider() } for more information about how
	 * content providers are located for the root of the viewer. The root
	 * content providers are calculated once. If a new element is supplied, a
	 * client must call {@link #update() } prior in order to reset the
	 * calculated root providers.
	 * </p>
	 * 
	 * @param anElement
	 *            An element from the tree (generally the input of the viewer)
	 * @return The set of content providers that can provide root elements for a
	 *         viewer.
	 */
	public ITreeContentProvider[] findRootContentProviders(Object anElement) {
		if (rootContentProviders != null) {
			return rootContentProviders;
		}
		synchronized (this) {
			if (rootContentProviders == null) {
				rootContentProviders = extractContentProviders(findRootContentExtensions(anElement));

			}
		}
		return rootContentProviders;
	}

	/**
	 * 
	 * Return all of the label providers that are enabled for the given element.
	 * A label provider is 'enabled' if its corresponding content provider
	 * returned the element, or the element is described in the content
	 * extension's <b>triggerPoints</b> expression.
	 * 
	 * @param anElement
	 *            An element from the tree (any element contributed to the
	 *            tree).
	 * @return The set of label providers that may be able to provide a valid
	 *         (non-null) label.
	 */
	public ILabelProvider[] findRelevantLabelProviders(Object anElement) {
		return extractLabelProviders(findContentExtensionsWithPossibleChild(
				anElement, false));
	}

	/**
	 * Search for extensions that declare the given element in their
	 * <b>triggerPoints</b> expression.
	 * 
	 * @param anElement
	 *            The element to use in the query
	 * @return The set of {@link INavigatorContentExtension}s that are
	 *         <i>visible</i> and <i>active</i> for this content service and
	 *         either declared through a
	 *         <b>org.eclipse.ui.navigator.viewer/viewerContentBinding</b> to
	 *         be a root element or have a <b>triggerPoints</b> expression that
	 *         is <i>enabled</i> for the given element.
	 */
	public Set findRootContentExtensions(Object anElement) {
		return findRootContentExtensions(anElement, true);
	}

	/**
	 * Search for extensions that declare the given element in their
	 * <b>triggerPoints</b> expression.
	 * 
	 * @param anElement
	 *            The element to use in the query
	 * @param toRespectViewerRoots
	 *            True respect the <b>viewerContentBinding</b>s, False will
	 *            look only for matching <b>triggerPoints</b> expressions.
	 * @return The set of {@link INavigatorContentExtension}s that are
	 *         <i>visible</i> and <i>active</i> for this content service and
	 *         either declared through a
	 *         <b>org.eclipse.ui.navigator.viewer/viewerContentBinding</b> to
	 *         be a root element or have a <b>triggerPoints</b> expression that
	 *         is <i>enabled</i> for the given element.
	 */
	public Set findRootContentExtensions(Object anElement,
			boolean toRespectViewerRoots) {

		SortedSet rootExtensions = new TreeSet(
				ExtensionPriorityComparator.INSTANCE);
		if (toRespectViewerRoots
				&& viewerDescriptor.hasOverriddenRootExtensions()) {

			NavigatorContentDescriptor[] descriptors = CONTENT_DESCRIPTOR_REGISTRY
					.getAllContentDescriptors();

			NavigatorContentExtension extension = null;
			for (int i = 0; i < descriptors.length; i++) {
				if (isActive(descriptors[i].getId())
						&& isRootExtension(descriptors[i].getId())) {
					extension = getExtension(descriptors[i]);
					if (!extension.hasLoadingFailed()) {
						rootExtensions.add(extension);
					}
				}
			}
		}
		if (rootExtensions.isEmpty()) {
			return findContentExtensionsByTriggerPoint(anElement);
		}
		return rootExtensions;
	}

	/**
	 * Search for extensions that declare the given element in their
	 * <b>possibleChildren</b> expression.
	 * 
	 * @param anElement
	 *            The element to use in the query
	 * @return The set of {@link INavigatorContentExtension}s that are
	 *         <i>visible</i> and <i>active</i> for this content service and
	 *         have a <b>possibleChildren</b> expression that is <i>enabled</i>
	 *         for the given element.
	 */
	public Set findOverrideableContentExtensionsForPossibleChild(
			Object anElement) {
		Set overrideableExtensions = new TreeSet(
				ExtensionPriorityComparator.INSTANCE);
		Set descriptors = findDescriptorsWithPossibleChild(anElement);
		for (Iterator iter = descriptors.iterator(); iter.hasNext();) {
			INavigatorContentDescriptor descriptor = (INavigatorContentDescriptor) iter
					.next();
			if (descriptor.hasOverridingExtensions()) {
				overrideableExtensions.add(getExtension(descriptor));
			}
		}
		return overrideableExtensions;
	}

	/**
	 * Search for extensions that declare the given element in their
	 * <b>triggerPoints</b> expression.
	 * 
	 * @param anElement
	 *            The element to use in the query
	 * @return The set of {@link INavigatorContentExtension}s that are
	 *         <i>visible</i> and <i>active</i> for this content service and
	 *         have a <b>triggerPoints</b> expression that is <i>enabled</i>
	 *         for the given element.
	 */
	public Set findContentExtensionsByTriggerPoint(Object anElement) {
		return findContentExtensionsByTriggerPoint(anElement, true);
	}

	/**
	 * Search for extensions that declare the given element in their
	 * <b>triggerPoints</b> expression.
	 * 
	 * @param anElement
	 *            The element to use in the query
	 * @param toLoadIfNecessary
	 *            True will force the load of the extension, False will not
	 * @return The set of {@link INavigatorContentExtension}s that are
	 *         <i>visible</i> and <i>active</i> for this content service and
	 *         have a <b>triggerPoints</b> expression that is <i>enabled</i>
	 *         for the given element.
	 */
	public Set findContentExtensionsByTriggerPoint(Object anElement,
			boolean toLoadIfNecessary) {
		Set enabledDescriptors = findDescriptorsByTriggerPoint(anElement);
		return extractDescriptorInstances(enabledDescriptors, toLoadIfNecessary);
	}

	/**
	 * Search for extensions that declare the given element in their
	 * <b>possibleChildren</b> expression.
	 * 
	 * @param anElement
	 *            The element to use in the query
	 * @return The set of {@link INavigatorContentExtension}s that are
	 *         <i>visible</i> and <i>active</i> for this content service and
	 *         have a <b>possibleChildren</b> expression that is <i>enabled</i>
	 *         for the given element.
	 */
	public Set findContentExtensionsWithPossibleChild(Object anElement) {
		return findContentExtensionsWithPossibleChild(anElement, true);
	}

	/**
	 * Search for extensions that declare the given element in their
	 * <b>possibleChildren</b> expression.
	 * 
	 * @param anElement
	 *            The element to use in the query
	 * @param toLoadIfNecessary
	 *            True will force the load of the extension, False will not
	 * @return The set of {@link INavigatorContentExtension}s that are
	 *         <i>visible</i> and <i>active</i> for this content service and
	 *         have a <b>possibleChildren</b> expression that is <i>enabled</i>
	 *         for the given element.
	 */
	public Set findContentExtensionsWithPossibleChild(Object anElement,
			boolean toLoadIfNecessary) {
		Set enabledDescriptors = findDescriptorsWithPossibleChild(anElement);
		return extractDescriptorInstances(enabledDescriptors, toLoadIfNecessary);
	}

	/**
	 * Remember that the elements in the given array came from the given source
	 * 
	 * @param source
	 *            The descriptor of the extension that contributed the set of
	 *            elements.
	 * @param elements
	 *            An array of elements from the given source.
	 */
	public synchronized void rememberContribution(
			NavigatorContentDescriptor source, Object[] elements) {

		if (source != null && elements != null) {
			for (int i = 0; i < elements.length; i++) {
				getContributionMemory().put(elements[i], source);
			}
		}
	}

	/**
	 * Remember that the elements in the given array came from the given source
	 * 
	 * @param source
	 *            The descriptor of the extension that contributed the set of
	 *            elements.
	 * @param element
	 *            An element from the given source.
	 */
	public synchronized void rememberContribution(
			NavigatorContentDescriptor source, Object element) {
		if (source != null && element != null) {
			getContributionMemory().put(element, source);
		}
	}

	/**
	 * 
	 * @param element
	 *            The element contributed by the descriptor to be returned
	 * @return The descriptor that contributed the element or null.
	 * @see #findContentExtensionsWithPossibleChild(Object)
	 */
	public NavigatorContentDescriptor getSourceOfContribution(Object element) {
		return (NavigatorContentDescriptor) getContributionMemory()
				.get(element);
	}

	/**
	 * @return Returns the contributionMemory.
	 */
	public Map getContributionMemory() {
		if (contributionMemory != null) {
			return contributionMemory;
		}
		synchronized (this) {
			if (contributionMemory == null) {
				contributionMemory = new WeakHashMap();
			}
		}

		return contributionMemory;
	}

	/**
	 * Search for extensions that declare the given element in their
	 * <b>triggerPoints</b> expression.
	 * 
	 * @param anElement
	 *            The element to use in the query
	 * @return The set of {@link INavigatorContentDescriptor}s that are
	 *         <i>visible</i> and <i>active</i> for this content service and
	 *         have a <b>triggerPoints</b> expression that is <i>enabled</i>
	 *         for the given element.
	 */
	public Set findDescriptorsByTriggerPoint(Object anElement) {

		NavigatorContentDescriptor descriptor = getSourceOfContribution(anElement);
		Set result = new HashSet();
		if (descriptor != null) {
			result.add(descriptor);
		}
		result.addAll(CONTENT_DESCRIPTOR_REGISTRY
				.findDescriptorsForTriggerPoint(anElement, assistant));
		return result;
	}

	/**
	 * Search for extensions that declare the given element in their
	 * <b>possibleChildren</b> expression.
	 * 
	 * @param anElement
	 *            The element to use in the query
	 * @return The set of {@link INavigatorContentDescriptor}s that are
	 *         <i>visible</i> and <i>active</i> for this content service and
	 *         have a <b>possibleChildren</b> expression that is <i>enabled</i>
	 *         for the given element.
	 */
	public Set findDescriptorsWithPossibleChild(Object anElement) {

		NavigatorContentDescriptor descriptor = getSourceOfContribution(anElement);
		Set result = new HashSet();
		if (descriptor != null) {
			result.add(descriptor);
		}
		result.addAll(CONTENT_DESCRIPTOR_REGISTRY
				.findDescriptorsForPossibleChild(anElement, assistant));
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.navigator.INavigatorContentService#onExtensionActivation(java.lang.String,
	 *      java.lang.String, boolean)
	 */
	public void onExtensionActivation(String aViewerId,
			String[] aNavigatorExtensionId, boolean toEnable) {
		synchronized (this) {
			try {
				NavigatorContentDescriptor key;
				NavigatorContentExtension extension;
				for (Iterator iter = contentExtensions.keySet().iterator(); iter.hasNext();) {
					key = (NavigatorContentDescriptor) iter.next();
					INavigatorActivationService activation = getActivationService();
					if(!activation.isNavigatorExtensionActive(key.getId())) {
						extension = (NavigatorContentExtension) contentExtensions.get(key);
						iter.remove();
						/* There really shouldn't be any way that this 
							can be null, but just to be safe */					
						if(extension != null) {
							extension.dispose();
						}
					}
				}
			} catch (RuntimeException e) { 
				String msg = e.getMessage() != null ? e.getMessage() : e.toString();
				NavigatorPlugin.logError(0, msg, e);
			}
		} 
		update();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.navigator.INavigatorContentService#update()
	 */
	public void update() {
		rootContentProviders = null;
		if (structuredViewerManager != null) {
			structuredViewerManager.safeRefresh();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.navigator.INavigatorContentService#getViewerId()
	 */
	public final String getViewerId() {
		return viewerDescriptor.getViewerId();
	}

	/**
	 * 
	 * @param aDescriptorKey
	 *            A descriptor
	 * @return The cached NavigatorContentExtension from the descriptor
	 */
	public final NavigatorContentExtension getExtension(
			INavigatorContentDescriptor aDescriptorKey) {
		return getExtension(aDescriptorKey, true);
	}

	/**
	 * 
	 * @param aDescriptorKey
	 * @param toLoadIfNecessary
	 *            True if the extension should be loaded if it is not already.
	 * @return The instance of the extension for the given descriptor key.
	 */
	public final NavigatorContentExtension getExtension(
			INavigatorContentDescriptor aDescriptorKey,
			boolean toLoadIfNecessary) {
		/* Query and return the relevant descriptor instance */
		NavigatorContentExtension extension = (NavigatorContentExtension) contentExtensions
				.get(aDescriptorKey);
		if (extension != null || !toLoadIfNecessary) {
			return extension;
		}

		/*
		 * If the descriptor instance hasn't been created yet, then we need to
		 * (1) verify that it wasn't added by another thread, (2) create and add
		 * the result into the map
		 */
		synchronized (this) {
			extension = (NavigatorContentExtension) contentExtensions
					.get(aDescriptorKey);
			if (extension == null) {
				contentExtensions.put(aDescriptorKey,
						(extension = new NavigatorContentExtension(
								(NavigatorContentDescriptor) aDescriptorKey,
								this, structuredViewerManager)));
				notifyListeners(extension);
			}
		}
		return extension;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.navigator.INavigatorContentService#getViewerDescriptor()
	 */
	public INavigatorViewerDescriptor getViewerDescriptor() {
		return viewerDescriptor;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.navigator.INavigatorContentService#restoreState(org.eclipse.ui.IMemento)
	 */
	public void restoreState(final IMemento aMemento) {
		synchronized (this) {
			for (Iterator extensionItr = getExtensions().iterator(); extensionItr
					.hasNext();) {
				final NavigatorContentExtension element = (NavigatorContentExtension) extensionItr
						.next();
				ISafeRunnable runnable = new ISafeRunnable() {
					public void run() throws Exception {
						element.restoreState(aMemento);

					}

					public void handleException(Throwable exception) {
						NavigatorPlugin.logError(0,
								"Could not restore state for Common Navigator content extension" //$NON-NLS-1$
										+ element.getId(), exception);

					}
				};
				SafeRunner.run(runnable);

			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.navigator.INavigatorContentService#saveState(org.eclipse.ui.IMemento)
	 */
	public void saveState(IMemento aMemento) {
		synchronized (this) {
			for (Iterator extensionItr = getExtensions().iterator(); extensionItr
					.hasNext();) {
				NavigatorContentExtension element = (NavigatorContentExtension) extensionItr
						.next();
				element.saveState(aMemento);
			}
		}
	}

	public boolean isActive(String anExtensionId) {
		return assistant.isActive(anExtensionId);
	}

	public boolean isVisible(String anExtensionId) {
		return assistant.isVisible(anExtensionId);
	}

	protected final Collection getExtensions() {
		return (contentExtensions.size() > 0) ? Collections
				.unmodifiableCollection(contentExtensions.values())
				: Collections.EMPTY_LIST;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.navigator.INavigatorContentService#addListener(org.eclipse.ui.internal.navigator.extensions.INavigatorContentServiceListener)
	 */
	public void addListener(INavigatorContentServiceListener aListener) {
		listeners.add(aListener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.navigator.INavigatorContentService#getFilterService()
	 */
	public INavigatorFilterService getFilterService() {
		if (navigatorFilterService == null) {
			navigatorFilterService = new NavigatorFilterService(this);
		}
		return navigatorFilterService;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.navigator.INavigatorContentService#getFilterService()
	 */
	public INavigatorSorterService getSorterService() {
		if (navigatorSorterService == null) {
			navigatorSorterService = new NavigatorSorterService(this);
		}
		return navigatorSorterService;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.navigator.INavigatorContentService#getFilterService()
	 */
	public INavigatorPipelineService getPipelineService() {
		if (navigatorPipelineService == null) {
			navigatorPipelineService = new NavigatorPipelineService(this);
		}
		return navigatorPipelineService;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.navigator.INavigatorContentService#getDnDService()
	 */
	public INavigatorDnDService getDnDService() {
		if (navigatorDnDService == null) {
			navigatorDnDService = new NavigatorDnDService(this);
		}
		return navigatorDnDService;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.navigator.INavigatorContentService#getActivationService()
	 */
	public INavigatorActivationService getActivationService() {

		if (navigatorActivationService == null) {
			navigatorActivationService = new NavigatorActivationService(this);
		}
		return navigatorActivationService;
	}
	
	/**
	 * Non-API method to return a shell.
	 * @return A shell associated with the current viewer (if any) or <b>null</b>.
	 */
	public Shell getShell() {
		if(structuredViewerManager != null && structuredViewerManager.getViewer() != null) {
			return structuredViewerManager.getViewer().getControl().getShell();
		}
		return null;
	}

	protected boolean isRootExtension(String anExtensionId) {
		return assistant.isRootExtension(anExtensionId);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.internal.navigator.INavigatorContentService#removeListener(org.eclipse.ui.internal.navigator.extensions.INavigatorContentServiceListener)
	 */
	public void removeListener(INavigatorContentServiceListener aListener) {
		listeners.remove(aListener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "ContentService[" + viewerDescriptor.getViewerId() + "]"; //$NON-NLS-1$//$NON-NLS-2$
	}

	private void notifyListeners(NavigatorContentExtension aDescriptorInstance) {

		if (listeners.size() == 0) {
			return;
		}
		INavigatorContentServiceListener listener = null;
		List failedListeners = null;
		for (Iterator listenersItr = listeners.iterator(); listenersItr
				.hasNext();) {
			try {
				listener = (INavigatorContentServiceListener) listenersItr
						.next();
				listener.onLoad(aDescriptorInstance);
			} catch (RuntimeException re) {
				if (failedListeners == null) {
					failedListeners = new ArrayList();
				}
				failedListeners.add(listener);
			}
		}
		if (failedListeners != null) {
			listeners.removeAll(failedListeners);
		}
	}

	private ITreeContentProvider[] extractContentProviders(
			Set theDescriptorInstances) {
		if (theDescriptorInstances.size() == 0) {
			return NO_CONTENT_PROVIDERS;
		}
		List resultProvidersList = new ArrayList();
		for (Iterator itr = theDescriptorInstances.iterator(); itr.hasNext();) {
			resultProvidersList.add(((NavigatorContentExtension) itr.next())
					.internalGetContentProvider());
		}
		return (ITreeContentProvider[]) resultProvidersList
				.toArray(new ITreeContentProvider[resultProvidersList.size()]);
	}

	private Set extractDescriptorInstances(Set theDescriptors,
			boolean toLoadAllIfNecessary) {
		if (theDescriptors.size() == 0) {
			return Collections.EMPTY_SET;
		}
		Set resultInstances = new TreeSet(ExtensionPriorityComparator.INSTANCE);
		for (Iterator descriptorIter = theDescriptors.iterator(); descriptorIter
				.hasNext();) {
			NavigatorContentExtension extension = getExtension(
					(NavigatorContentDescriptor) descriptorIter.next(),
					toLoadAllIfNecessary);
			if (extension != null) {
				resultInstances.add(extension);

			}
		}
		return resultInstances;
	}

	private ILabelProvider[] extractLabelProviders(Set theDescriptorInstances) {
		if (theDescriptorInstances.size() == 0) {
			return NO_LABEL_PROVIDERS;
		}
		List resultProvidersList = new ArrayList();
		for (Iterator itr = theDescriptorInstances.iterator(); itr.hasNext();) {
			resultProvidersList.add(((NavigatorContentExtension) itr.next())
					.getLabelProvider());
		}
		return (ILabelProvider[]) resultProvidersList
				.toArray(new ILabelProvider[resultProvidersList.size()]);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.navigator.INavigatorContentService#getSaveableService()
	 */
	public INavigatorSaveablesService getSaveablesService() {
		if (navigatorSaveablesService == null) {
			navigatorSaveablesService = new NavigatorSaveablesService(this);
		}
		return navigatorSaveablesService;
	}

}
