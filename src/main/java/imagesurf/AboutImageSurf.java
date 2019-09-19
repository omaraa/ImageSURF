package imagesurf;

import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Attr;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "Plugins>Segmentation>ImageSURF>About ImageSURF",
		headless = true)
public class AboutImageSurf implements Command {

	@Parameter(type = ItemIO.OUTPUT)
	private String ImageSurf;

	@Override
	public void run() {
		ImageSurf = "ABOUT IMAGESURF v1.1.4-20190920\n\n" +
				"ImageSURF (Image Segmentation Using Random Forests) is an ImageJ2 plugin for pixel-based " +
				"image segmentation that uses an optimised implementation of the random forests machine learning " +
				"algorithm and a set of selected image features." +
				"\n\n" +
				"For instructions on how to use ImageSURF visit https://github.com/omaraa/ImageSURF/wiki" +
				"\n\n" +
				"To get help, report bugs or request features visit the ImageSURF GitHub issues page at "+
				"https://github.com/omaraa/ImageSURF/issues";
	}

	public static void main(String[] args)
	{
		// create the ImageJ application context with all available services
		final ImageJ ij = net.imagej.Main.launch(args);

		ij.command().run(AboutImageSurf.class, true);
	}
}
