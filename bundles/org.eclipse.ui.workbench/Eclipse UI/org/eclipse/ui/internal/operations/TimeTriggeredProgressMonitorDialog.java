/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM - Initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.operations;


import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

/**
 * The TimeTriggeredProgressMonitorDialog is a progress monitor dialog that only
 * opens if the runnable provided exceeds the specified long operation time.
 * 
 * @since 3.1
 */
public class TimeTriggeredProgressMonitorDialog extends ProgressMonitorDialog {

	/**
	 * The time considered to be the long operation time.
	 */
	private int longOperationTime;

	/**
	 * The time at which the dialog should be opened.
	 */
	private long triggerTime = -1;

	/**
	 * Whether or not we've already opened a dialog.
	 */
	private boolean dialogOpened = false;
	
	/**
	 * Wrappered monitor so we can check ticks and open the dialog when
	 * appropriate
	 */
	private IProgressMonitor wrapperedMonitor;

	/**
	 * Create a new instance of the receiver.
	 * 
	 * @param parent
	 *            the parent of the dialog
	 * @param longOperationTime
	 *            the time (in milliseconds) considered to be a long enough
	 *            execution time to warrant opening a dialog.
	 */
	public TimeTriggeredProgressMonitorDialog(Shell parent,
			int longOperationTime) {
		super(parent);
		setOpenOnRun(false);
		this.longOperationTime = longOperationTime;
	}

   /**
	 * Create a monitor for the receiver that wrappers the superclasses monitor.
	 * 
	 */
    public void createWrapperedMonitor() {
        wrapperedMonitor = new IProgressMonitor() {

            IProgressMonitor superMonitor = TimeTriggeredProgressMonitorDialog.super
                    .getProgressMonitor();

            /*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.core.runtime.IProgressMonitor#beginTask(java.lang.String,
			 *      int)
			 */
            @Override
			public void beginTask(String name, int totalWork) {
                superMonitor.beginTask(name, totalWork);
                checkTicking();
            }

            /**
			 * Check if we have ticked in the last 800ms.
			 */
            private void checkTicking() {
            	if (triggerTime < 0) {
					triggerTime = System.currentTimeMillis() + longOperationTime;
				}
    			if (!dialogOpened && System.currentTimeMillis() > triggerTime) {
    				open();
    				dialogOpened = true;
    			}
            }



            /*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.core.runtime.IProgressMonitor#done()
			 */
            @Override
			public void done() {
                superMonitor.done();
                checkTicking();
            }

            /*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.core.runtime.IProgressMonitor#internalWorked(double)
			 */
            @Override
			public void internalWorked(double work) {
                superMonitor.internalWorked(work);
                checkTicking();
            }

            /*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.core.runtime.IProgressMonitor#isCanceled()
			 */
            @Override
			public boolean isCanceled() {
                return superMonitor.isCanceled();
            }

            /*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.core.runtime.IProgressMonitor#setCanceled(boolean)
			 */
            @Override
			public void setCanceled(boolean value) {
                superMonitor.setCanceled(value);

            }

            /*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.core.runtime.IProgressMonitor#setTaskName(java.lang.String)
			 */
            @Override
			public void setTaskName(String name) {
                superMonitor.setTaskName(name);
                checkTicking();

            }

            /*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.core.runtime.IProgressMonitor#subTask(java.lang.String)
			 */
            @Override
			public void subTask(String name) {
                superMonitor.subTask(name);
                checkTicking();
            }

            /*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.core.runtime.IProgressMonitor#worked(int)
			 */
            @Override
			public void worked(int work) {
                superMonitor.worked(work);
                checkTicking();

            }
        };
    }

    /*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.dialogs.ProgressMonitorDialog#getProgressMonitor()
	 */
    @Override
	public IProgressMonitor getProgressMonitor() {
        if (wrapperedMonitor == null) {
			createWrapperedMonitor();
		}
        return wrapperedMonitor;
    }
    
   /*
    * (non-Javadoc) 
    * 
    * @see org.eclipse.jface.operations.IRunnableContext#run(boolean, boolean, IRunnableWithProgress)
    */
    @Override
	public void run(final boolean fork, final boolean cancelable,
            final IRunnableWithProgress runnable) throws InvocationTargetException,
            InterruptedException {
    	final InvocationTargetException[] invokes = new InvocationTargetException[1];
        final InterruptedException[] interrupt = new InterruptedException[1];
        Runnable dialogWaitRunnable = new Runnable() {
    		@Override
			public void run() {
    			try {
    				TimeTriggeredProgressMonitorDialog.super.run(fork, cancelable, runnable);
    			} catch (InvocationTargetException e) {
    				invokes[0] = e;
    			} catch (InterruptedException e) {
    				interrupt[0]= e;
    			}
    		}
        };
        final Display display = PlatformUI.getWorkbench().getDisplay();
        if (display == null) {
			return;
		}
        //show a busy cursor until the dialog opens
        BusyIndicator.showWhile(display, dialogWaitRunnable);
        if (invokes[0] != null) {
            throw invokes[0];
        }
        if (interrupt[0] != null) {
            throw interrupt[0];
        }
     }
}
