package org.eclipse.jface.text;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */


/**
 * This event is sent to implementers of <code>ITextListener</code>. It represents a 
 * change applied to text viewer. The change is specified as a replace command using 
 * offset, length, inserted text, and replaced text. The text viewer issues a text event
 * after the viewer has been changed either in response to a change of the viewer's document
 * or when the viewer's visual content has been changed. In the first case, the text event
 * also carries the original document event. Depending on the viewer's presentation mode,
 * the text event coordinates are different from the document event's coordinates.
 * Client's other than text viewer's don't create instances of this class.
 *
 * @see ITextListener
 * @see ITextViewer
 * @see DocumentEvent
 */
public class TextEvent {
	
	/** Start offset of the change */ 
	private int fOffset;
	/** The length of the change */
	private int fLength;
	/** Inserted text */
	private String fText;
	/** Replaced text */
	private String fReplacedText;
	/** The original document event, may by null */
	private DocumentEvent fDocumentEvent;
	/** The redraw state of the viewer issuing this event */
	private boolean fViewerRedrawState;
	
	/**
	 * Creates a new <code>TextEvent</code> based on the specification.
	 */
	protected TextEvent(int offset, int length, String text, String replacedText, DocumentEvent event, boolean viewerRedrawState) {
		fOffset= offset;
		fLength= length;
		fText= text;
		fReplacedText= replacedText;
		fDocumentEvent= event;
		fViewerRedrawState= viewerRedrawState;
	}
			
	/**
	 * Returns the offset of the event.
	 *
	 * @return the offset of the event
	 */
	public int getOffset() {
		return fOffset;
	}
		
	/**
	 * Returns the length of the event.
	 *
	 * @return the length of the event
	 */
	public int getLength() {
		return fLength;
	}
	
	/**
	 * Returns the text of the event.
	 *
	 * @return the text of the event
	 */
	public String getText() {
		return fText;
	}
	
	/**
	 * Returns the text replaced by this event.
	 *
	 * @return the text replaced by this event
	 */
	public String getReplacedText() {
		return fReplacedText;
	}
	
	/**
	 * Returns the corresponding document event that caused the viewer change
	 *
	 * @return the corresponding document event, <code>null</code> if a visual change only
	 */
	public DocumentEvent getDocumentEvent() {
		return fDocumentEvent;
	}
	
	/**
	 * Returns the viewer's redraw state.
	 * 
	 * @return <code>true</code> if the viewer's redraw state is <code>true</code>
	 */
	public boolean getViewerRedrawState() {
		return fViewerRedrawState;
	}
}