package au.gov.ga.worldwind.common.layers.tiled.image.delegate;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import au.gov.ga.worldwind.common.layers.tiled.image.delegate.colortoalpha.ColorToAlphaTransformerDelegate;
import au.gov.ga.worldwind.common.layers.tiled.image.delegate.nearestneighbor.NearestNeighborTextureTileFactoryDelegate;
import au.gov.ga.worldwind.common.layers.tiled.image.delegate.stripingfilter.StripingFilterTransformerDelegate;
import au.gov.ga.worldwind.common.layers.tiled.image.delegate.transparentcolor.TransparentColorTransformerDelegate;

/**
 * Factory which creates delegates from a string definition.
 * 
 * @author Michael de Hoog
 */
public class DelegateFactory
{
	private static Map<Class<? extends Delegate>, Delegate> classToInstanceMap =
			new HashMap<Class<? extends Delegate>, Delegate>();
	private static Map<Class<? extends Delegate>, Class<? extends Delegate>> replacementClasses =
			new HashMap<Class<? extends Delegate>, Class<? extends Delegate>>();

	/**
	 * Static constructor which registers all the default delegates. Custom
	 * delegates can be added using the registerDelegate() function.
	 */
	static
	{
		registerDelegate(URLRequesterDelegate.class);
		registerDelegate(LocalRequesterDelegate.class);

		registerDelegate(HttpRetrieverFactoryDelegate.class);
		registerDelegate(PassThroughZipRetrieverFactoryDelegate.class);

		registerDelegate(TextureTileFactoryDelegate.class);
		registerDelegate(NearestNeighborTextureTileFactoryDelegate.class);

		registerDelegate(MaskImageReaderDelegate.class);

		registerDelegate(ColorToAlphaTransformerDelegate.class);
		registerDelegate(TransparentColorTransformerDelegate.class);
		registerDelegate(StripingFilterTransformerDelegate.class);
	}

	/**
	 * Register a delegate class.
	 * 
	 * @param delegateClass
	 *            Class to register
	 */
	public static void registerDelegate(Class<? extends Delegate> delegateClass)
	{
		if (!classToInstanceMap.containsKey(delegateClass))
		{
			try
			{
				Constructor<? extends Delegate> c = delegateClass.getDeclaredConstructor();
				c.setAccessible(true);
				classToInstanceMap.put(delegateClass, c.newInstance());
			}
			catch (Exception e)
			{
				throw new IllegalArgumentException("Error instanciating delegate", e);
			}
		}
	}

	/**
	 * Register a delegate class which is to be replaced by another delegate in
	 * the createDelegate() method. The replacement class should be able to be
	 * instanciated by the same string definition as the class it is replacing.
	 * 
	 * @param fromClass
	 *            Class to be replaced
	 * @param toClass
	 *            Class to replace fromClass with
	 */
	public static void registerReplacementClass(Class<? extends Delegate> fromClass,
			Class<? extends Delegate> toClass)
	{
		replacementClasses.put(fromClass, toClass);
	}

	/**
	 * Create a new delgate from a string definition.
	 * 
	 * @param definition
	 * @return New delegate corresponding to {@code definition}
	 */
	public static Delegate createDelegate(String definition)
	{
		for (Delegate delegate : classToInstanceMap.values())
		{
			Delegate d = delegate.fromDefinition(definition);
			if (d != null)
			{
				if (replacementClasses.containsKey(d.getClass()))
				{
					Class<? extends Delegate> replacementClass =
							replacementClasses.get(d.getClass());
					Delegate instance = classToInstanceMap.get(replacementClass);
					Delegate newInstance = instance.fromDefinition(definition);
					if (newInstance != null)
					{
						return newInstance;
					}
				}
				return d;
			}
		}
		throw new IllegalArgumentException("Don't know how to create delegate from definition: "
				+ definition);
	}
}
