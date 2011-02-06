package au.gov.ga.worldwind.tiler.ribbon;

import java.awt.Insets;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import au.gov.ga.worldwind.tiler.util.Util;

/**
 * Contains the contextual information for a ribbon tiling job
 */
public class RibbonTilingContext {

	// Tiling parameters
	private int tilesize = 512;
	private File sourceFile;
	private File outputLocation;
	private File tilesetRoot;
	private Insets insets = new Insets(0,0,0,0);
	private String format = null;
	
	// Flags
	private boolean copySource = true; 
	private boolean mask = false;
	private boolean generateTilingLog = true;
	private boolean hideStdOut = false;
	private boolean generateLayerDefinition = true;
	
	// Layer definition parameters
	private File layerDefinitionFile;
	private List<String> delegateStrings = new ArrayList<String>();
	
	
	// Writing streams
	private static OutputStream NULL_STREAM = new OutputStream()
	{
		@Override
		public void write(int b) throws IOException {}
	};
	private static Writer NULL_WRITER = new OutputStreamWriter(NULL_STREAM);
	private static Writer STD_WRITER = new OutputStreamWriter(System.out);
	private Writer logWriter = null;
	
	public Writer getStdWriter()
	{
		return hideStdOut ? NULL_WRITER : STD_WRITER;
	}
	
	public Writer getLogWriter()
	{
		if (!generateTilingLog)
		{
			return NULL_WRITER;
		}
		if (logWriter == null)
		{
			try
			{
				logWriter = new OutputStreamWriter(new FileOutputStream(new File(getTilesetRoot(), getLogFileName())));
			}
			catch (FileNotFoundException e)
			{
				return NULL_WRITER;
			}
		}
		return logWriter; 
	}
	
	private String getLogFileName()
	{
		return getTilesetName() + ".log";
	}
	
	public String getFormat()
	{
		if (format == null)
		{
			format = mask ? "png" : "jpg";
		}
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}
	
	public String getTilesetName()
	{
		return sourceFile == null ? null : Util.stripExtension(sourceFile.getName());
	}
	
	public File getSourceLocation()
	{
		return sourceFile == null ? null : sourceFile.getParentFile();
	}

	public File getTilesetRoot()
	{
		if (tilesetRoot == null)
		{
			tilesetRoot = new File(outputLocation, getTilesetName());
			tilesetRoot.mkdir();
		}
		return tilesetRoot;
	}
	
	public boolean isMask() {
		return mask;
	}

	public void setIsMask(boolean mask) {
		this.mask = mask;
	}

	public int getTilesize() {
		return tilesize;
	}

	public void setTilesize(int tilesize) {
		this.tilesize = tilesize;
	}

	public File getSourceFile() {
		return sourceFile;
	}

	public void setSourceFile(File sourceFile) {
		this.sourceFile = sourceFile;
	}

	public File getOutputLocation() {
		return outputLocation;
	}

	public void setOutputLocation(File outputLocation) {
		this.outputLocation = outputLocation;
	}
	
	public Insets getInsets() {
		return insets;
	}
	
	public void setInsets(Insets insets) {
		this.insets = insets;
	}

	public void setCopySource(boolean copySource) {
		this.copySource = copySource;
	}

	public boolean isCopySource() {
		return copySource;
	}

	public void setGenerateTilingLog(boolean generateTilingLog) {
		this.generateTilingLog = generateTilingLog;
	}

	public boolean isGenerateTilingLog() {
		return generateTilingLog;
	}

	public void setHideStdOut(boolean hideStdOut) {
		this.hideStdOut = hideStdOut;
	}

	public boolean isHideStdOut() {
		return hideStdOut;
	}

	public void setTilesetRoot(File destination) {
		this.tilesetRoot = destination;
	}
	
	public boolean isGenerateLayerDefinition() {
		return generateLayerDefinition;
	}

	public void setGenerateLayerDefinition(boolean generateLayerDefinition) {
		this.generateLayerDefinition = generateLayerDefinition;
	}
	
	public File getLayerDefinitionFile() {
		if (layerDefinitionFile == null)
		{
			layerDefinitionFile = new File(getTilesetRoot(), getTilesetName() + ".xml");
			try
			{
				layerDefinitionFile.createNewFile();
			}
			catch (IOException e)
			{
				
			}
		}
		return layerDefinitionFile;
	}

	public void setLayerDefinitionFile(File layerDefinitionFile) {
		this.layerDefinitionFile = layerDefinitionFile;
	}
	
	public List<String> getDelegateStrings()
	{
		return delegateStrings;
	}
	
	public void setDelegateStrings(List<String> delegateStrings)
	{
		this.delegateStrings = delegateStrings;
	}
}
