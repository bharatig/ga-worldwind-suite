package au.gov.ga.worldwind.animator.application.effects.depthoffield;

import static au.gov.ga.worldwind.animator.util.message.AnimationMessageConstants.getDepthOfFieldNearParameterNameKey;
import static au.gov.ga.worldwind.common.util.message.MessageSourceAccessor.getMessage;
import gov.nasa.worldwind.avlist.AVList;

import org.w3c.dom.Element;

import au.gov.ga.worldwind.animator.animation.Animation;
import au.gov.ga.worldwind.animator.animation.io.AnimationFileVersion;
import au.gov.ga.worldwind.animator.animation.io.AnimationIOConstants;
import au.gov.ga.worldwind.animator.animation.parameter.BasicBezierParameterValue;
import au.gov.ga.worldwind.animator.animation.parameter.ParameterBase;
import au.gov.ga.worldwind.animator.animation.parameter.ParameterValue;
import au.gov.ga.worldwind.animator.application.effects.EffectParameterBase;
import au.gov.ga.worldwind.common.util.Validate;

public class DepthOfFieldNearParameter extends EffectParameterBase
{
	public DepthOfFieldNearParameter(String name, Animation animation, DepthOfFieldEffect effect)
	{
		super(name, animation, effect);
	}

	DepthOfFieldNearParameter()
	{
		super();
	}
	
	@Override
	protected String getDefaultName()
	{
		return getMessage(getDepthOfFieldNearParameterNameKey());
	}

	@Override
	public ParameterValue getCurrentValue()
	{
		return new BasicBezierParameterValue(animation.getView().getNearClipDistance(), animation.getCurrentFrame(),
				this);
	}

	@Override
	protected void doApplyValue(double value)
	{
		((DepthOfFieldEffect) getEffect()).setNear(value);
	}

	@Override
	protected String getXmlElementName(AnimationIOConstants constants)
	{
		return constants.getDepthOfFieldNearElementName();
	}
	
	@Override
	public double getDefaultValue(int frame)
	{
		//TODO is this right, or should it be retrieved from the camera?
		return animation.getView().getNearClipDistance();
	}

	@Override
	protected ParameterBase createParameterFromXml(String name, Animation animation, Element element,
			Element parameterElement, AnimationFileVersion version, AVList context)
	{
		AnimationIOConstants constants = version.getConstants();
		DepthOfFieldEffect parameterEffect = (DepthOfFieldEffect) context.getValue(constants.getCurrentEffectKey());
		Validate.notNull(parameterEffect,
				"No effect found in the context. Expected one under the key '" + constants.getCurrentEffectKey() + "'.");

		return new DepthOfFieldNearParameter(name, animation, parameterEffect);
	}
}