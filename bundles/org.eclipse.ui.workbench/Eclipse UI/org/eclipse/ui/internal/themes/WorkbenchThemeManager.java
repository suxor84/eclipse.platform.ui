/*******************************************************************************
 * Copyright (c) 2004, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.themes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.eclipse.core.commands.common.EventManager;
import org.eclipse.core.runtime.Platform;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.e4.ui.internal.css.swt.definition.IThemeElementDefinitionOverridable;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.services.IStylingEngine;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.misc.StatusUtil;
import org.eclipse.ui.internal.util.PrefUtil;
import org.eclipse.ui.statushandlers.StatusManager;
import org.eclipse.ui.themes.ITheme;
import org.eclipse.ui.themes.IThemeManager;
import org.osgi.service.event.EventHandler;

/**
 * Theme manager for the Workbench.
 * 
 * @since 3.0
 */
public class WorkbenchThemeManager extends EventManager implements
		IThemeManager {
	public static RGB EMPTY_COLOR_VALUE = new RGB(0, 1, 2);

	public static FontData[] EMPRY_FONT_DATA_VALUE = PreferenceConverter.FONTDATA_ARRAY_DEFAULT_DEFAULT;

	private static final String SYSTEM_DEFAULT_THEME = "org.eclipse.ui.ide.systemDefault";//$NON-NLS-1$

	private static WorkbenchThemeManager instance;

	private IEclipseContext context;

	private IEventBroker eventBroker;

	public static interface Events {
		public static final String TOPIC = "org/eclipse/ui/internal/themes/WorkbenchThemeManager"; //$NON-NLS-1$

		public static final String THEME_REGISTRY_RESTYLED = TOPIC + "/themeRegistryRestyled"; //$NON-NLS-1$

		public static final String THEME_REGISTRY_MODIFIED = TOPIC + "/themeRegistryModified"; //$NON-NLS-1$
	}

	/**
	 * Returns the singelton instance of the WorkbenchThemeManager
	 * 
	 * @return singleton instance
	 */
	public static WorkbenchThemeManager getInstance() {
		if (instance == null) {
			if (PlatformUI.getWorkbench().getDisplay() != null) {
				PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
					@Override
					public void run() {
						getInternalInstance();
					}
				});
			}
		}
		return instance;
	}

	/**
	 * Initialize the singleton theme manager. Must be called in the UI thread.
	 * 
	 * @return the theme manager.
	 */
	private static synchronized WorkbenchThemeManager getInternalInstance() {
		if (instance == null) {
			instance = new WorkbenchThemeManager();
			instance.getCurrentTheme(); // initialize the current theme
		}
		return instance;
	}

	private ITheme currentTheme;

	private IPropertyChangeListener currentThemeListener = new IPropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent event) {
			firePropertyChange(event);
			if (event.getSource() instanceof FontRegistry) {
				JFaceResources.getFontRegistry().put(event.getProperty(),
						(FontData[]) event.getNewValue());
			} else if (event.getSource() instanceof ColorRegistry) {
				JFaceResources.getColorRegistry().put(event.getProperty(),
						(RGB) event.getNewValue());
			}
		}
	};

	private ColorRegistry defaultThemeColorRegistry;

	private FontRegistry defaultThemeFontRegistry;

	private IThemeRegistry themeRegistry;

	private Map themes = new HashMap(7);

	private EventHandler themeChangedHandler = new WorkbenchThemeChangedHandler();

	private EventHandler themeRegistryModifiedHandler = new ThemeRegistryModifiedHandler();

	/*
	 * Initialize the WorkbenchThemeManager.
	 * Determine the default theme according to the following rules:
	 *   1) If we're in HC mode then default to system default
	 *   2) Otherwise, if preference already set (e.g. via plugin_customization.ini), then observe that value
	 *   3) Otherwise, use our default
	 * Call dispose when we close.
	 */
	private WorkbenchThemeManager() {
		defaultThemeColorRegistry = new ColorRegistry(PlatformUI.getWorkbench()
				.getDisplay());

		defaultThemeFontRegistry = new FontRegistry(PlatformUI.getWorkbench()
				.getDisplay());

		// copy the font values from preferences.
		FontRegistry jfaceFonts = JFaceResources.getFontRegistry();
		for (Iterator i = jfaceFonts.getKeySet().iterator(); i.hasNext();) {
			String key = (String) i.next();
			defaultThemeFontRegistry.put(key, jfaceFonts.getFontData(key));
		}

		//Theme might be set via plugin_configuration.ini
		String themeId = PrefUtil.getAPIPreferenceStore().getDefaultString(IWorkbenchPreferenceConstants.CURRENT_THEME_ID);

		//If not set, use default
		if (themeId.length() == 0)
			themeId = IThemeManager.DEFAULT_THEME;

		final boolean highContrast = Display.getCurrent().getHighContrast();

		Display.getCurrent().addListener(SWT.Settings, new Listener() {
			@Override
			public void handleEvent(Event event) {
				updateThemes();
			}
		});

		// If in HC, *always* use the system default.
		// This ignores any default theme set via plugin_customization.ini
		if (highContrast)
			themeId = SYSTEM_DEFAULT_THEME;

		PrefUtil.getAPIPreferenceStore().setDefault(
				IWorkbenchPreferenceConstants.CURRENT_THEME_ID, themeId);

		context = (IEclipseContext) Workbench.getInstance().getService(IEclipseContext.class);
		eventBroker = (IEventBroker) Workbench.getInstance().getService(IEventBroker.class);
		if (eventBroker != null) {
			eventBroker.subscribe(UIEvents.UILifeCycle.THEME_CHANGED, themeChangedHandler);
			eventBroker.subscribe(IThemeEngine.Events.THEME_CHANGED, themeChangedHandler);
			eventBroker.subscribe(Events.THEME_REGISTRY_MODIFIED, themeRegistryModifiedHandler);
		}
	}

	/*
	 * Update existing theme contents, descriptors, and registries.
	 * Reread the themes and recompute the registries.
	 */	
	private void updateThemes() {
		//reread the themes since their descriptors have changed in value
        ThemeRegistryReader reader = new ThemeRegistryReader();
        reader.readThemes(Platform.getExtensionRegistry(),(ThemeRegistry) getThemeRegistry());   

        //DEFAULT_THEME is not in getThemes() list so must be handled special
        ThemeElementHelper.populateRegistry(getTheme(IThemeManager.DEFAULT_THEME), getThemeRegistry().getColors(), PrefUtil.getInternalPreferenceStore());			
        
        IThemeDescriptor[] themeDescriptors = getThemeRegistry().getThemes();

       	for (int i=0; i < themeDescriptors.length; i++) {
        	IThemeDescriptor themeDescriptor = themeDescriptors[i];
    		ITheme theme = (ITheme) themes.get(themeDescriptor);
    		//If theme is in our themes table then its already been populated
    		if (theme != null) {
                ColorDefinition[] colorDefinitions = themeDescriptor.getColors();
              
               if (colorDefinitions.length > 0) {
                	ThemeElementHelper.populateRegistry(theme, colorDefinitions,PrefUtil.getInternalPreferenceStore());
                }
    		}
		}
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.themes.IThemeManager#addPropertyChangeListener(org.eclipse.jface.util.IPropertyChangeListener)
	 */
	@Override
	public void addPropertyChangeListener(IPropertyChangeListener listener) {
		addListenerObject(listener);
	}

	/**
	 * Disposes all ThemeEntries.
	 */
	public void dispose() {
		if (eventBroker != null) {
			eventBroker.unsubscribe(themeChangedHandler);
			eventBroker.unsubscribe(themeRegistryModifiedHandler);
		}

		for (Iterator i = themes.values().iterator(); i.hasNext();) {
			ITheme theme = (ITheme) i.next();
			theme.removePropertyChangeListener(currentThemeListener);
			theme.dispose();
		}
		themes.clear();
	}

	private boolean doSetCurrentTheme(String id) {
		ITheme oldTheme = currentTheme;
		ITheme newTheme = getTheme(id);
		if (oldTheme != newTheme && newTheme != null) {
			currentTheme = newTheme;
			return true;
		}

		return false;
	}

	protected void firePropertyChange(PropertyChangeEvent event) {
		Object[] listeners = getListeners();

		for (int i = 0; i < listeners.length; i++) {
			((IPropertyChangeListener) listeners[i]).propertyChange(event);
		}
	}

	protected void firePropertyChange(String changeId, ITheme oldTheme,
			ITheme newTheme) {

		PropertyChangeEvent event = new PropertyChangeEvent(this, changeId,
				oldTheme, newTheme);
		firePropertyChange(event);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.themes.IThemeManager#getCurrentTheme()
	 */
	@Override
	public ITheme getCurrentTheme() {
		if (currentTheme == null) {
			String themeId = PrefUtil.getAPIPreferenceStore().getString(
					IWorkbenchPreferenceConstants.CURRENT_THEME_ID);

			if (themeId == null) // missing preference
				setCurrentTheme(IThemeManager.DEFAULT_THEME);
			else {
				setCurrentTheme(themeId);
				if (currentTheme == null) { // still null - the preference
											// didn't resolve to a proper theme
					setCurrentTheme(IThemeManager.DEFAULT_THEME);
					StatusManager
							.getManager()
							.handle(
									StatusUtil
											.newStatus(
													PlatformUI.PLUGIN_ID,
													"Could not restore current theme: " + themeId, null)); //$NON-NLS-1$
				}
			}			
		}
		return currentTheme;
	}

	/**
	 * Return the default color registry.
	 * 
	 * @return the default color registry
	 */
	public ColorRegistry getDefaultThemeColorRegistry() {
		return defaultThemeColorRegistry;
	}

	/**
	 * Return the default font registry.
	 * 
	 * @return the default font registry
	 */
	public FontRegistry getDefaultThemeFontRegistry() {
		return defaultThemeFontRegistry;
	}

	private ITheme getTheme(IThemeDescriptor td) {
		ITheme theme = (ITheme) themes.get(td);
		if (theme == null) {
			theme = new Theme(td);
			themes.put(td, theme);
		}
		return theme;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.themes.IThemeManager#getTheme(java.lang.String)
	 */
	@Override
	public ITheme getTheme(String id) {
		if (id.equals(IThemeManager.DEFAULT_THEME)) {
			return getTheme((IThemeDescriptor) null);
		}

		IThemeDescriptor td = getThemeRegistry().findTheme(id);
		if (td == null) {
			return null;
		}
		return getTheme(td);
	}

	/**
	 * Answer the IThemeRegistry for the Workbench
	 */
	private IThemeRegistry getThemeRegistry() {
		if (themeRegistry == null) {
			themeRegistry = WorkbenchPlugin.getDefault().getThemeRegistry();
		}
		return themeRegistry;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.themes.IThemeManager#removePropertyChangeListener(org.eclipse.jface.util.IPropertyChangeListener)
	 */
	@Override
	public void removePropertyChangeListener(IPropertyChangeListener listener) {
		removeListenerObject(listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.themes.IThemeManager#setCurrentTheme(java.lang.String)
	 */
	@Override
	public void setCurrentTheme(String id) {
		ITheme oldTheme = currentTheme;
		if (WorkbenchThemeManager.getInstance().doSetCurrentTheme(id)) {
			firePropertyChange(CHANGE_CURRENT_THEME, oldTheme,
					getCurrentTheme());
			if (oldTheme != null) {
				oldTheme.removePropertyChangeListener(currentThemeListener);
			}
			currentTheme.addPropertyChangeListener(currentThemeListener);

			// update the preference if required.
			if (!PrefUtil.getAPIPreferenceStore().getString(
					IWorkbenchPreferenceConstants.CURRENT_THEME_ID).equals(id)) {
				PrefUtil.getAPIPreferenceStore().setValue(
						IWorkbenchPreferenceConstants.CURRENT_THEME_ID, id);
				PrefUtil.saveAPIPrefs();
			}

			// update the jface registries
			{
				ColorRegistry jfaceColors = JFaceResources.getColorRegistry();
				ColorRegistry themeColors = currentTheme.getColorRegistry();
				for (Iterator i = themeColors.getKeySet().iterator(); i
						.hasNext();) {
					String key = (String) i.next();
					jfaceColors.put(key, themeColors.getRGB(key));
				}
			}
			{
				FontRegistry jfaceFonts = JFaceResources.getFontRegistry();
				FontRegistry themeFonts = currentTheme.getFontRegistry();
				for (Iterator i = themeFonts.getKeySet().iterator(); i
						.hasNext();) {
					String key = (String) i.next();
					jfaceFonts.put(key, themeFonts.getFontData(key));
				}
			}
			{
				if (oldTheme != null && eventBroker != null) {
					eventBroker.send(UIEvents.UILifeCycle.THEME_CHANGED, null);
					eventBroker.send(UIEvents.UILifeCycle.THEME_DEFINITION_CHANGED,
							context.get(MApplication.class.getName()));
				}
			}
		}
	}
	
	public static class WorkbenchThemeChangedHandler implements EventHandler {
		@Override
		public void handleEvent(org.osgi.service.event.Event event) {
			IStylingEngine engine = getStylingEngine();
			ThemeRegistry themeRegistry = getThemeRegistry();
			FontRegistry fontRegistry = getFontRegistry();
			ColorRegistry colorRegistry = getColorRegistry();

			resetThemeRegistries(themeRegistry, fontRegistry, colorRegistry);
			overrideAlreadyExistingDefinitions(event, engine, themeRegistry, fontRegistry,
					colorRegistry);
			addNewDefinitions(event, engine, themeRegistry, fontRegistry, colorRegistry);

			sendThemeRegistryRestyledEvent();
		}

		protected IStylingEngine getStylingEngine() {
			return (IStylingEngine) getContext().get(IStylingEngine.SERVICE_NAME);
		}

		protected ThemeRegistry getThemeRegistry() {
			return (ThemeRegistry) getContext().get(IThemeRegistry.class.getName());
		}

		protected FontRegistry getFontRegistry() {
			return getColorsAndFontsTheme().getFontRegistry();
		}

		protected ColorRegistry getColorRegistry() {
			return getColorsAndFontsTheme().getColorRegistry();
		}

		protected void sendThemeRegistryRestyledEvent() {
			IEventBroker eventBroker = (IEventBroker) getContext()
					.get(IEventBroker.class.getName());
			eventBroker.send(Events.THEME_REGISTRY_RESTYLED, null);
		}

		protected ITheme getColorsAndFontsTheme() {
			return WorkbenchThemeManager.getInstance().getCurrentTheme();
		}

		private IEclipseContext getContext() {
			return WorkbenchThemeManager.getInstance().context;
		}

		protected org.eclipse.e4.ui.css.swt.theme.ITheme getTheme(org.osgi.service.event.Event event) {
			org.eclipse.e4.ui.css.swt.theme.ITheme theme = (org.eclipse.e4.ui.css.swt.theme.ITheme) event
					.getProperty(IThemeEngine.Events.THEME);
			if (theme == null) {
				IThemeEngine themeEngine = (IThemeEngine) getContext().get(
						IThemeEngine.class.getName());
				theme = themeEngine != null ? themeEngine.getActiveTheme() : null;
			}
			return theme;
		}

		// At this moment we don't remove the definitions added by CSS since we
		// don't want to modify the 3.x theme registries api
		protected void resetThemeRegistries(ThemeRegistry themeRegistry, FontRegistry fontRegistry,
				ColorRegistry colorRegistry) {
			for (FontDefinition def : themeRegistry.getFonts()) {
				if (def.isOverridden()) {
					def.resetToDefaultValue();
					fontRegistry.put(def.getId(), def.getValue() != null ? def.getValue()
							: EMPRY_FONT_DATA_VALUE);
				}
			}
			for (ColorDefinition def : themeRegistry.getColors()) {
				if (def.isOverridden()) {
					def.resetToDefaultValue();
					colorRegistry.put(def.getId(), def.getValue() != null ? def.getValue()
							: EMPTY_COLOR_VALUE);
				}
			}
		}

		protected void overrideAlreadyExistingDefinitions(org.osgi.service.event.Event event,
				IStylingEngine engine, ThemeRegistry themeRegistry, FontRegistry fontRegistry,
				ColorRegistry colorRegistry) {
			IPreferenceStore store = PrefUtil.getInternalPreferenceStore();
			org.eclipse.e4.ui.css.swt.theme.ITheme cssTheme = getTheme(event);
			ITheme theme = getColorsAndFontsTheme();

			for (FontDefinition fontDefinition : themeRegistry.getFonts()) {
				engine.style(fontDefinition);
				if (fontDefinition.isOverridden()) {
					populateDefinition(cssTheme, theme, fontRegistry, fontDefinition, store);
					if (!fontDefinition.isModifiedByUser()) {
						fontRegistry.put(fontDefinition.getId(), fontDefinition.getValue());
					}
				}
			}
			for (ColorDefinition colorDefinition : themeRegistry.getColors()) {
				engine.style(colorDefinition);
				if (colorDefinition.isOverridden()) {
					populateDefinition(cssTheme, theme, colorRegistry, colorDefinition, store);
					if (!colorDefinition.isModifiedByUser()) {
						colorRegistry.put(colorDefinition.getId(), colorDefinition.getValue());
					}
				}
			}
		}

		private void addNewDefinitions(org.osgi.service.event.Event event, IStylingEngine engine,
				ThemeRegistry themeRegistry, FontRegistry fontRegistry, ColorRegistry colorRegistry) {
			IPreferenceStore store = PrefUtil.getInternalPreferenceStore();
			org.eclipse.e4.ui.css.swt.theme.ITheme cssTheme = getTheme(event);
			ITheme theme = getColorsAndFontsTheme();
			ThemesExtension themesExtension = createThemesExtension();
			engine.style(themesExtension);

			for (IThemeElementDefinitionOverridable<?> definition : themesExtension
					.getDefinitions()) {
				engine.style(definition);
				if (definition.isOverridden() && definition instanceof FontDefinition) {
					addFontDefinition((FontDefinition) definition, themeRegistry, fontRegistry);
					populateDefinition(cssTheme, theme, fontRegistry, (FontDefinition) definition,
							store);
				} else if (definition.isOverridden() && definition instanceof ColorDefinition) {
					addColorDefinition((ColorDefinition) definition, themeRegistry, colorRegistry);
					populateDefinition(cssTheme, theme, colorRegistry,
							(ColorDefinition) definition, store);
				}
			}
		}

		private void addFontDefinition(FontDefinition definition, ThemeRegistry themeRegistry,
				FontRegistry fontRegistry) {
			if (themeRegistry.findFont(definition.getId()) == null) {
				themeRegistry.add(definition);
				fontRegistry.put(definition.getId(), definition.getValue());
			}
		}

		private void addColorDefinition(ColorDefinition definition, ThemeRegistry themeRegistry,
				ColorRegistry colorRegistry) {
			if (themeRegistry.findColor(definition.getId()) == null) {
				themeRegistry.add(definition);
				colorRegistry.put(definition.getId(), definition.getValue());
			}
		}

		protected ThemesExtension createThemesExtension() {
			return new ThemesExtension();
		}

		protected void populateDefinition(org.eclipse.e4.ui.css.swt.theme.ITheme cssTheme,
				ITheme theme, ColorRegistry registry, ColorDefinition definition,
				IPreferenceStore store) {
			ThemeElementHelper.populateDefinition(cssTheme, theme, registry, definition, store);
		}

		protected void populateDefinition(org.eclipse.e4.ui.css.swt.theme.ITheme cssTheme,
				ITheme theme, FontRegistry registry, FontDefinition definition,
				IPreferenceStore store) {
			ThemeElementHelper.populateDefinition(cssTheme, theme, registry, definition, store);
		}
	}

	public static class ThemeRegistryModifiedHandler implements EventHandler {
		@Override
		public void handleEvent(org.osgi.service.event.Event event) {
			populateThemeRegistries(getThemeRegistry(), getFontRegistry(), getColorRegistry(),
					getTheme(), getColorsAndFontsTheme());
			sendThemeDefinitionChangedEvent();
		}

		protected org.eclipse.e4.ui.css.swt.theme.ITheme getTheme() {
			IThemeEngine themeEngine = (IThemeEngine) getContext()
					.get(IThemeEngine.class.getName());
			return themeEngine != null ? themeEngine.getActiveTheme() : null;
		}

		protected ThemeRegistry getThemeRegistry() {
			return (ThemeRegistry) getContext().get(IThemeRegistry.class.getName());
		}

		protected FontRegistry getFontRegistry() {
			return getColorsAndFontsTheme().getFontRegistry();
		}

		protected ColorRegistry getColorRegistry() {
			return getColorsAndFontsTheme().getColorRegistry();
		}

		protected ITheme getColorsAndFontsTheme() {
			return WorkbenchThemeManager.getInstance().getCurrentTheme();
		}

		private IEclipseContext getContext() {
			return WorkbenchThemeManager.getInstance().context;
		}

		protected void sendThemeDefinitionChangedEvent() {
			MApplication application = (MApplication) getContext()
					.get(MApplication.class.getName());
			getInternalInstance().eventBroker.send(UIEvents.UILifeCycle.THEME_DEFINITION_CHANGED,
					application);
		}

		protected void populateThemeRegistries(ThemeRegistry themeRegistry,
				FontRegistry fontRegistry, ColorRegistry colorRegistry,
				org.eclipse.e4.ui.css.swt.theme.ITheme cssTheme, ITheme theme) {
			IPreferenceStore store = PrefUtil.getInternalPreferenceStore();
			for (FontDefinition definition : themeRegistry.getFonts()) {
				if (definition.isOverridden() || definition.isAddedByCss()) {
					populateDefinition(cssTheme, theme, fontRegistry, definition, store);
				}
			}
			for (ColorDefinition definition : themeRegistry.getColors()) {
				if (definition.isOverridden() || definition.isAddedByCss()) {
					populateDefinition(cssTheme, theme, colorRegistry, definition, store);
				}
			}
		}

		protected void populateDefinition(org.eclipse.e4.ui.css.swt.theme.ITheme cssTheme,
				ITheme theme, ColorRegistry registry, ColorDefinition definition,
				IPreferenceStore store) {
			ThemeElementHelper.populateDefinition(cssTheme, theme, registry, definition, store);
		}

		protected void populateDefinition(org.eclipse.e4.ui.css.swt.theme.ITheme cssTheme,
				ITheme theme, FontRegistry registry, FontDefinition definition,
				IPreferenceStore store) {
			ThemeElementHelper.populateDefinition(cssTheme, theme, registry, definition, store);
		}
	}

}
