/**
 * A text-merge template compiled into a form to facilitate generation
 *   from EntityLists.
 */
package spiralcraft.pioneer.data.text;

//import com.spiralcraft.data.lang.ExpressionBinding;
//import com.spiralcraft.data.lang.ValueContext;
//import com.spiralcraft.data.lang.IterationContext;
//import com.spiralcraft.data.lang.BoundIterator;

import com.spiralcraft.data.ContextProvider;
import com.spiralcraft.data.Context;
import com.spiralcraft.data.EntityDescriptor;
import com.spiralcraft.data.Entity;
import com.spiralcraft.data.EntityList;
import com.spiralcraft.data.FieldDescriptor;
import com.spiralcraft.data.FieldTranslator;
import com.spiralcraft.data.DataEnvironment;
import com.spiralcraft.data.SimpleOrderDescriptor;
import com.spiralcraft.data.OrderDescriptor;

import com.spiralcraft.data.util.RelationUtil;

import com.solidis.template.Parser;
import com.solidis.template.TemplateHandler;
import com.solidis.template.ParseException;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.io.IOException;

import java.util.Date;
import java.text.DateFormat;

import com.spiralcraft.xml.TagReader;
import com.spiralcraft.xml.Attribute;
import com.spiralcraft.xml.ParserContext;

import spiralcraft.text.Encoder;

import spiralcraft.util.StringUtil;

public class MergeTemplate
{

  private Map _contextProviders;
  private DataEnvironment _dataEnvironment;
  private ValueContext _defaultContext;
  private DateFormat _defaultDateFormat;
  private Stack _tagStack=new Stack();
  private Tag _currentTag=new DocumentTag();
  private TagReader _tagReader=new TagReader();
  private ClassLoader _classLoader;
  private Encoder _encoder;
  private boolean _stripWhitespace;

  public void setDefaultDateFormat(DateFormat format)
  { _defaultDateFormat=format;
  }

  public void setEncoder(Encoder encoder)
  { _encoder=encoder;
  }
  
  public void compileTemplate(String template)
    throws ParseException
  {
    if (template==null)
    { template="";
    }
    Parser parser=new Parser();
    try
    { parser.parse(new StringReader(template),new Handler());
    }
    catch (IOException x)
    { throw new ParseException(x.toString());
    }
  }

  public void output(Writer out)
    throws IOException
  { 
    try
    { _currentTag.write(out);
    }
    catch (RuntimeException x)
    { 
      x.printStackTrace();
      throw x;

    }
  }

  public String output()
  {
    StringWriter writer=new StringWriter();
    try
    { _currentTag.write(writer);
    }
    catch (IOException x)
    { 
      // Should never happen with StringWriter
      x.printStackTrace();
    }
    catch (RuntimeException x)
    { 
      x.printStackTrace();
      throw x;
    }

    return writer.toString();

  }

  public void setDataEnvironment(DataEnvironment env)
  { _dataEnvironment=env;
  }

  public void setContextProviders(Map contextProviders)
  { _contextProviders=contextProviders;
  }

  public void setDefaultContext(ValueContext context)
  { _defaultContext=context;
  }

  private ExpressionBinding makeBinding(String expression)
    throws ParseException
  {
    //* Channel instead of ExpressionBinding- this is really the
    //*   guts of Focus
    
    ExpressionBinding binding = new ExpressionBinding();
    binding.setExpression(expression);
    if (_dataEnvironment!=null)
    { binding.setDataEnvironment(_dataEnvironment);
    }
    if (_contextProviders!=null)
    { binding.setContextProviders(_contextProviders);
    }
    if (_defaultContext!=null)
    { binding.setDefaultContext(_defaultContext);
    }
    try
    { binding.compile();
    }
    catch (Exception x)
    { throw new ParseException(x);
    }
    return binding;
  }

  class Handler
    implements TemplateHandler
  {
    public void handleContent(int offset,char[] text,int start,int len)
    { new TextFragment(new String(text,start,len));
    }

    public void handleScript(int offset,char[] script,int start,int len)
      throws ParseException
    { new TagFragment(new String(script,start,len));
    }
  }

  interface Fragment
  {
    public void write(Writer out)
      throws IOException;
  }

  class TextFragment
    implements Fragment
  {
    private String _text;

    public TextFragment(String text)
    { 
      if (_stripWhitespace)
      { _text=StringUtil.removeChars(text,"\r\n\t ");
      }
      else
      { _text=text;
      }
      _currentTag.addFragment(this);
    }

    public void write(Writer out)
      throws IOException
    { out.write(_text);
    }
  }

  class TagFragment
    implements Fragment
  {
    private Tag _tag;

    public TagFragment(String tag)
      throws ParseException
    { 
      tag=tag.trim();
      if (tag.startsWith("/"))
      { 
        String name=tag.substring(1);
        if (!_currentTag.getName().equals(name))
        { 
          throw new ParseException
            ("Mismatched end tag. Found <%/"+name+"%>, expected <%/"+_currentTag.getName()
            );
        }
        _currentTag.closeDefinition();
        _currentTag=(Tag) _tagStack.pop();
        return;
      }
      else if (tag.startsWith("="))
      {
        if (!tag.endsWith("/"))
        {
          throw new ParseException
            ("Expression tag must be closed (must end with \"/\")"
            );
        }
        if (tag.startsWith("=="))
        { _tag=new ExpressionTag(tag.substring(0,tag.length()-1).substring(2).trim(),false);
        }
        else
        { _tag=new ExpressionTag(tag.substring(0,tag.length()-1).substring(1).trim(),true);
        }
        _tag.setParent(_currentTag);
      }
      else
      {
        try
        { _tagReader.readTag(new ParserContext(tag));
        }
        catch (com.spiralcraft.xml.ParseException x)
        { 
          x.printStackTrace();
          throw new ParseException(x.toString());
        }

        String name=_tagReader.getTagName().intern();
        if (name=="if")
        { _tag=new IfTag(_tagReader.getAttributeList());
        }
        else if (name=="else")
        { 
          if (!tag.endsWith("/"))
          {
            throw new ParseException
              ("Else tag must be closed (must end with \"/\")"
              );
          }
          _tag=new ElseTag(_tagReader.getAttributeList());
        }
        else if (name=="iterate")
        { _tag=new IterateTag(_tagReader.getAttributeList());
        }
        else if (name=="document")
        { _tag=new DocumentTag(_tagReader.getAttributeList());
        }
        else if (name=="header")
        { _tag=new HeaderTag(_tagReader.getAttributeList());
        }
        else if (name=="style")
        { _tag=new StyleTag(_tagReader.getAttributeList());
        }
        else if (name=="before")
        { _tag=new StyleBeforeTag(_tagReader.getAttributeList());
        }
        else if (name=="after")
        { _tag=new StyleAfterTag(_tagReader.getAttributeList());
        }
        else if (name=="crlf")
        { _tag=new CrlfTag(_tagReader.getAttributeList());
        }
        else
        { throw new ParseException("Unknown tag "+name);
        }

        if (tag.endsWith("/"))
        { _tag.closeDefinition();
        }
        _tag.setParent(_currentTag);
      }

      _currentTag.addFragment(this);

      if (!tag.endsWith("/"))
      { 
        _tagStack.push(_currentTag);
        _currentTag=_tag;
      }
      
    
    }

    public void write(Writer out)
      throws IOException
    { _tag.write(out);
    }

  }

  abstract class Tag
  {
 
    private String _expression;
    protected List fragments=new LinkedList();
    protected String name;
    private Tag parent;
    private HashMap _styleMap;

    public void setParent(Tag val)
    { parent=val;
    }

    public String getName()
    { return name;
    }

    public void addFragment(Fragment fragment)
    { fragments.add(fragment);
    }

    public void write(Writer out)
      throws IOException
    {
      Iterator it=fragments.iterator();
      while (it.hasNext())
      { ((Fragment) it.next()).write(out);
      }
    }

    public void closeDefinition()
      throws ParseException
    {
    }

    public Tag findAncestorWithClass(Class val)
    { 
      if (val.isAssignableFrom(getClass()))
      { return this;
      }
      else if (parent!=null)
      { return parent.findAncestorWithClass(val);
      }
      else
      { return null;
      }
    }

    public void putStyle(String name,StyleTag style)
    { 
      if (_styleMap==null)
      { _styleMap=new HashMap();
      }
      _styleMap.put(name,style);
    }

    public StyleTag getStyle(String name)
    {
      StyleTag style=null;
      if (_styleMap!=null)
      { style=(StyleTag) _styleMap.get(name);
      }

      if (style==null && parent!=null)
      { style=(StyleTag) parent.getStyle(name);
      }

      return style;
    }
  }

  class DocumentTag
    extends Tag
  {
    { name="document";
    }

    public DocumentTag()
    { }
    
    public DocumentTag(List attributes)
      throws ParseException
    {
      Iterator it=attributes.iterator();
      while (it.hasNext())
      {
        Attribute attribute=(Attribute) it.next();
        String name=attribute.getName().intern();
        if (name=="stripWhitespace")
        { _stripWhitespace=attribute.getValue().equals("true");
        }
        else
        { throw new ParseException("Unkown attribute '"+name+"' in <%document ... %>");
        }
      }
    }
  }

  class StyleTag
    extends Tag
  {
    { name="style";
    }

    private String _styleName;
    private StylePartTag _styleBefore;
    private StylePartTag _styleAfter;
    private StyleTag _referencedTag;
    private Tag _parent;
    
    
    public StyleTag(List attributes)
      throws ParseException
    {
      Iterator it=attributes.iterator();
      while (it.hasNext())
      {
        Attribute attribute=(Attribute) it.next();
        String name=attribute.getName().intern();
        if (name=="name")
        { _styleName=attribute.getValue();
        }
        else
        { throw new ParseException("Unkown attribute '"+name+"' in <%style ... %>");
        }
      }
      
      if (_styleName==null)
      { throw new ParseException("Attribute '"+name+"' is required in <%style ... %>");
      }

      _parent=_currentTag;
      _referencedTag=_parent.getStyle(_styleName);
    }

    public void setStyleBefore(StylePartTag tag)
    { _styleBefore=tag;
    }

    public void setStyleAfter(StylePartTag tag)
    { _styleAfter=tag;
    }

    public void write(Writer out)
      throws IOException
    { 
      if (_referencedTag!=null)
      { 
        _referencedTag.writeBefore(out);
        super.write(out);
        _referencedTag.writeAfter(out);        
      }
    }

    public void writeBefore(Writer out)
      throws IOException
    { 
      if (_styleBefore!=null)
      { _styleBefore.write(out);
      }
    }

    public void writeAfter(Writer out)
      throws IOException
    { 
      if (_styleAfter!=null)
      { _styleAfter.write(out);
      }
    }

    public void closeDefinition()
      throws ParseException
    {
      if (_referencedTag==null 
          && _styleBefore==null 
          && _styleAfter==null
         )
      { throw new ParseException("Style '"+_styleName+"' no found");
      }
      else if (_styleBefore!=null || _styleAfter!=null)
      { _parent.putStyle(_styleName,this);
      }
    }

    /**
     * Use 'sub' styles defined in the referenced tag first
     */
    public StyleTag getStyle(String name)
    {
      StyleTag _style=_referencedTag.getStyle(name);
      if (_style==null)
      { _style=super.getStyle(name);
      }
      return _style;
    }
  }

  class StylePartTag
    extends Tag
  {

    public StylePartTag(List attributes)
      throws ParseException
    {
      Iterator it=attributes.iterator();
      while (it.hasNext())
      {
        if (false)
        {
        }
        else
        { throw new ParseException("Unkown attribute '"+name+"' in <%style ... %>");
        }
      }
    }
  }

  class StyleBeforeTag
    extends StylePartTag
  {
    { name="before";
    }

    public StyleBeforeTag(List attributes)
      throws ParseException
    { 
      super(attributes);
      if (_currentTag instanceof StyleTag)
      { ((StyleTag) _currentTag).setStyleBefore(this);
      }
      else 
      { throw new ParseException("'before' tag must be contained directly inside 'style' tag");
      }

    }
  }

  class StyleAfterTag
    extends StylePartTag
  {
    { name="after";
    }

    public StyleAfterTag(List attributes)
      throws ParseException
    { 
      super(attributes);
      if (_currentTag instanceof StyleTag)
      { ((StyleTag) _currentTag).setStyleAfter(this);
      }
      else 
      { throw new ParseException("'after' tag must be contained directly inside 'style' tag");
      }
    }
  }

  class IfTag
    extends Tag
  {
    { name="if";
    }

    private ExpressionBinding _binding;
    private List _positiveFragments;
    private List _negativeFragments;
    private boolean _boolean;

    public IfTag(List attributes)
      throws ParseException
    {
      Iterator it=attributes.iterator();
      while (it.hasNext())
      {
        Attribute attribute=(Attribute) it.next();
        String name=attribute.getName().intern();
        if (name=="expression")
        {
          _binding=makeBinding(attribute.getValue());

          
          if (_binding.getValueClass(_classLoader)==Boolean.class
              || _binding.getValueClass(_classLoader)==boolean.class
              )
          { _boolean=true;
          }
          
        }
        else
        {
          throw new ParseException("Unkown attribute '"+name+"' in <%if ... %>");
        }
      }
    }

    public void elsePart()
    { 
      _positiveFragments=fragments;
      _negativeFragments=new LinkedList();
      fragments=_negativeFragments;
    }

    public void write(Writer out)
      throws IOException
    {
      if (_boolean)
      {
        if ( ((Boolean) _binding.getValue()).booleanValue())
        { 
          if (_positiveFragments!=null)
          { 
            // Else part has been specified
            fragments=_positiveFragments;
          }
          super.write(out);
        }
        else
        {
          if (_negativeFragments!=null)
          { 
            // Else part has been specified
            fragments=_negativeFragments;
            super.write(out);
          }
        }
      }
      else
      {
        if (_binding.getValue()!=null)
        {
          if (_positiveFragments!=null)
          { 
            // Else part has been specified
            fragments=_positiveFragments;
          }
          super.write(out);
        }
        else
        {
          if (_negativeFragments!=null)
          { 
            // Else part has been specified
            fragments=_negativeFragments;
            super.write(out);
          }
        }
      }
    }
  }

  class CrlfTag
    extends Tag
  {
    { name="crlf";
    }
    
    public CrlfTag(List attributes)
      throws ParseException
    {
      if (attributes.size()>0)
      {
        throw new ParseException
          ("<%crlf cannot contain any attributes");
      }
    }

    public void write(Writer out)
      throws IOException
    { 
      out.write("\r\n");
      super.write(out);
    }
    
  }
  
  class HeaderTag
    extends Tag
  {
    { name="header";
    }

    private IterateTag _iterateTag;

    public HeaderTag(List attributes)
      throws ParseException
    {
      if (_currentTag!=null)
      { 
        _iterateTag
          =(IterateTag) _currentTag.findAncestorWithClass(IterateTag.class);
      }

      if (_iterateTag==null)
      { 
        throw new ParseException
          ("'header' tag must be contained in an 'iterate' tag");
      }
    }

    public void write(Writer out)
      throws IOException
    { 
      if (_iterateTag.isFirst())
      { super.write(out);
      }
    }
  }
  
  class ElseTag
    extends Tag
  {
    public ElseTag(List attributes)
      throws ParseException
    {
      if (_currentTag instanceof IfTag)
      { ((IfTag) _currentTag).elsePart();
      }
      else 
      { throw new ParseException("'else' tag must be contained directly inside 'if' tag");
      }

    }
    public void write(Writer out)
    { 
    }
  }
  
  class IterateTag
    extends Tag
  {
    { name="iterate";
    }

    private BoundIterator _iterator;
    private ExpressionBinding _binding;
    private String _variable;
    private ValueContext _oldDefaultContext;
    private boolean _first;
    private OrderDescriptor _order;
    private String _filterExpression;
    private String _expression;
    
    public IterateTag(List attributes)
      throws ParseException
    {
      Iterator it=attributes.iterator();
      while (it.hasNext())
      {
        Attribute attribute=(Attribute) it.next();
        String name=attribute.getName().intern();
        if (name=="expression")
        { _expression=attribute.getValue().intern();
        }
        else if (name=="variable")
        { _variable=attribute.getValue().intern();
        }
        else if (name=="order")
        { 
          try
          { _order=new SimpleOrderDescriptor(attribute.getValue().intern());
          }
          catch (java.text.ParseException x)
          { throw new ParseException(x);
          }
        }
        else if (name=="filter")
        { _filterExpression=attribute.getValue().intern();
        }
        else
        { throw new ParseException("Unkown attribute '"+name+"' in <%if ... %>");
        }
      }

      if (_expression==null)
      { throw new ParseException("Attribute 'expression' required for <%iterate ... %>");
      }
          
      _binding=makeBinding(_expression);
      
      if (!(_binding.getBoundContext()
             instanceof IterationContext
           )
         )
      {
        throw new ParseException
          ("Cannot iterate through "
          +_binding.getExpression()
          +" ("+_binding.getValueClass(_classLoader)+")"
          );
      }

      _oldDefaultContext=_defaultContext;

      _iterator=
        ((IterationContext) _binding.getBoundContext())
          .getBoundIterator();

      try
      { _iterator.setOrder(_order);
      }
      catch (com.spiralcraft.data.lang.ParseException x)
      { throw new ParseException(x);
      }
      _defaultContext=_iterator.getComponentContext();
      

    }

    public boolean isFirst()
    { return _first;
    }

    public void write(Writer out)
      throws IOException
    {
      _iterator.reset();
      _first=true;
      
      while (_iterator.hasNext())
      { 
        _iterator.next();
        super.write(out);
        _first=false;
      }
      
      
    }


    public void closeDefinition()
    {
      if (_oldDefaultContext!=null)
      { _defaultContext=_oldDefaultContext;
      }
    }
  }

  class ExpressionTag
    extends Tag
  {
    private ExpressionBinding _binding;
    private FieldTranslator _translator;
    private FieldDescriptor _descriptor;
    private boolean _useEncoding;


    public ExpressionTag(String expression,boolean useEncoding)
      throws ParseException
    {
      _binding=makeBinding(expression);
      _useEncoding=useEncoding;
      
      _descriptor
        =_binding.getFieldDescriptor();
      if (_descriptor!=null)
      { _translator=_descriptor.getTranslator();
      }
    }

    public void write(Writer out)
      throws IOException
    { 
      Object value=_binding.getValue();
      String translatedValue;
      if (value!=null)
      { 
        if (_defaultDateFormat!=null
            && value instanceof Date
            )
        { translatedValue=_defaultDateFormat.format((Date) value);
        }
        else if (_translator!=null)
        { translatedValue=_translator.translateToText(value);
        }
        else
        { translatedValue=value.toString();
        }
        if (_useEncoding && _encoder!=null)
        { _encoder.encode(translatedValue,out);
        }
        else
        { out.write(translatedValue);
        }
      }
    }
    
  }
}
