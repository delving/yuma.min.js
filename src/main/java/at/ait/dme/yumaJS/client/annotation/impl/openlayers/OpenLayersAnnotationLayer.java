package at.ait.dme.yumaJS.client.annotation.impl.openlayers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.ExportPackage;
import org.timepedia.exporter.client.Exportable;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Style.Overflow;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootPanel;

import at.ait.dme.yumaJS.client.YUMA;
import at.ait.dme.yumaJS.client.annotation.Annotatable;
import at.ait.dme.yumaJS.client.annotation.Annotation;
import at.ait.dme.yumaJS.client.annotation.editors.ResizableBoxEditor;
import at.ait.dme.yumaJS.client.annotation.editors.selection.BoundingBox;
import at.ait.dme.yumaJS.client.annotation.editors.selection.Range;
import at.ait.dme.yumaJS.client.annotation.impl.openlayers.api.Bounds;
import at.ait.dme.yumaJS.client.annotation.impl.openlayers.api.BoxMarker;
import at.ait.dme.yumaJS.client.annotation.impl.openlayers.api.BoxesLayer;
import at.ait.dme.yumaJS.client.annotation.impl.openlayers.api.LonLat;
import at.ait.dme.yumaJS.client.annotation.impl.openlayers.api.Map;
import at.ait.dme.yumaJS.client.annotation.impl.openlayers.api.Pixel;
import at.ait.dme.yumaJS.client.init.InitParams;

@Export
@ExportPackage("YUMA")
public class OpenLayersAnnotationLayer extends Annotatable implements Exportable {

	private static final String MEDIATYPE = "MAP";
	
	private String objectURI;

	private Map map;
	
	private BoxesLayer annotationLayer;
	
	private AbsolutePanel editingLayer;
	
	private HashMap<Annotation, OpenLayersAnnotationOverlay> annotations = 
		new HashMap<Annotation, OpenLayersAnnotationOverlay>();
	
	public OpenLayersAnnotationLayer(JavaScriptObject openLayersMap, String objectURI, InitParams params) {
		super(params);

		if (openLayersMap == null) 
			YUMA.fatalError("Error: OpenLayers map undefined (not initialized yet?)");
		
		map = new Map(openLayersMap);
		this.objectURI = objectURI;

		// TODO make annotation layer name configurable via init params
		annotationLayer = BoxesLayer.create("Annotations");
		map.addBoxesLayer(annotationLayer);
		
		HTML parentDiv = map.getDiv();
		editingLayer = new AbsolutePanel();
		editingLayer.setStyleName("openlayers-editing-layer");
		editingLayer.getElement().getStyle().setOverflow(Overflow.VISIBLE);
		editingLayer.setPixelSize(parentDiv.getOffsetWidth(), parentDiv.getOffsetHeight());		
		RootPanel.get().insert(editingLayer, parentDiv.getAbsoluteLeft(), parentDiv.getAbsoluteTop(), 0);
		
		if (getServerURL() != null) {
			fetchAnnotations(getServerURL());
		}
	}

	@Override
	public String getObjectURI() {
		return objectURI;
	}

	@Override
	public String getMediaType() {
		return MEDIATYPE;
	}
	
	@Override
	public String toFragment(BoundingBox bbox, Range range) {
		Pixel pxBottomLeft = Pixel.create(bbox.getX(), bbox.getY() + bbox.getHeight());
		Pixel pxTopRight = Pixel.create(bbox.getX() + bbox.getWidth(), bbox.getY());
		
		LonLat llBottomLeft = map.getLonLatFromPixel(pxBottomLeft);
		LonLat llTopRight = map.getLonLatFromPixel(pxTopRight);
		
		return "bbox=" 
			+ llBottomLeft.getLon() + "," 
			+ llBottomLeft.getLat() + ","
			+ llTopRight.getLon() + "," 
			+ llTopRight.getLat();
	}

	@Override
	public Range toRange(String fragment) {
		// Openlayers doesn't support range fragments
		return null;
	}

	@Override
	public BoundingBox toBoundingBox(String fragment) {
		Bounds bounds = toOpenLayersBounds(fragment);
		LonLat llBottomLeft = LonLat.create(bounds.getLeft(), bounds.getBottom());
		LonLat llTopRight = LonLat.create(bounds.getRight(), bounds.getTop());
		
		Pixel pxBottomLeft = map.getViewPortPixelFromLonLat(llBottomLeft);
		Pixel pxTopRight = map.getViewPortPixelFromLonLat(llTopRight);
		
		return BoundingBox.create(
				pxBottomLeft.getX(), 
				pxTopRight.getY(),
				pxTopRight.getX() - pxBottomLeft.getX(),
				pxBottomLeft.getY() - pxTopRight.getY());
	}
	
	private Bounds toOpenLayersBounds(String fragment) {
		String[] bbox = fragment.substring(5).split(",");
		if (bbox.length != 4)
			return null;
		
		return Bounds.create(
				Double.parseDouble(bbox[0]),
				Double.parseDouble(bbox[1]),
				Double.parseDouble(bbox[2]),
				Double.parseDouble(bbox[3]));
	}

	@Override
	protected void onWindowResize(int width, int height) {
		editingLayer.setPixelSize(width, height);
		RootPanel.get().setWidgetPosition(editingLayer, map.getDiv().getAbsoluteLeft(), map.getDiv().getAbsoluteTop());
	}

	@Override
	public void addAnnotation(Annotation annotation) {
		BoxMarker marker = BoxMarker.create(toOpenLayersBounds(annotation.getFragment()));
		annotationLayer.addMarker(marker);
		
		OpenLayersAnnotationOverlay overlay = 
			new OpenLayersAnnotationOverlay(this, annotation, marker, getLabels());

		annotations.put(annotation, overlay);
		sortOverlaysByArea();
		fireOnAnnotationCreated(annotation);
	}
	
	private void sortOverlaysByArea() {
		ArrayList<OpenLayersAnnotationOverlay> overlays = new ArrayList<OpenLayersAnnotationOverlay>();
		for (Annotation a : annotations.keySet()) {
			overlays.add(annotations.get(a));
		}
		Collections.sort(overlays);
		
		// Re-assign z-indexes
		int zIndex = 9010;
		for (OpenLayersAnnotationOverlay overlay : overlays) {
			overlay.setZIndex(zIndex);
			zIndex++;
		}
	}

	@Override
	public void removeAnnotation(Annotation annotation) {
		OpenLayersAnnotationOverlay overlay = annotations.get(annotation);
		if (overlay != null) {
			annotationLayer.removeMarker(overlay.getMarker());
			overlay.destroy();
			annotations.remove(annotation);
		}
	}

	@Override
	public void editAnnotation(Annotation a) {
		new ResizableBoxEditor(this, editingLayer, a);
	}
	
	public void createNewAnnotation() {
		editAnnotation(null);
	}
	
}
