package javaxt.json;
import javaxt.utils.Value;
import java.io.IOException;
import java.io.Writer;
import java.util.Map.Entry;

//******************************************************************************
//**  JSONObject
//******************************************************************************
/**
 *   Used to create and parse JSON documents. JSON documents are an unordered
 *   collection of name/value pairs. Its external form is a string wrapped in 
 *   curly braces with colons between the names and values, and commas between 
 *   the values and names.
 * 
 *   @author json.org
 *   @version 2016-08-15
 *
 ******************************************************************************/

public class JSONObject {
    
    private final java.util.LinkedHashMap<String, Object> map;

    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public JSONObject() {
        map = new java.util.LinkedHashMap<String, Object>();
    }
    

  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Construct a JSONObject from a source JSON text string. This is the most
   *  commonly used JSONObject constructor.
   *
   * @param source A string beginning with <code>{</code>&nbsp;<small>(left
   *  brace)</small> and ending with <code>}</code> &nbsp;<small>(right brace)
   *  </small>.
   */
    public JSONObject(String source) throws JSONException {
        this(new JSONTokener(source));
    }
   
    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Construct a JSONObject from a JSONTokener.
   */
    protected JSONObject(JSONTokener x) throws JSONException {
        this();
        char c;
        String key;

        if (x.nextClean() != '{') {
            throw x.syntaxError("A JSONObject text must begin with '{'");
        }
        for (;;) {
            c = x.nextClean();
            switch (c) {
            case 0:
                throw x.syntaxError("A JSONObject text must end with '}'");
            case '}':
                return;
            default:
                x.back();
                key = x.nextValue().toString();
            }

            // The key is followed by ':'.

            c = x.nextClean();
            if (c != ':') {
                throw x.syntaxError("Expected a ':' after a key");
            }
            
            // Use syntaxError(..) to include error location
            
            if (key != null) {
                // Check if key exists
                if (map.get(key) != null) {
                    // key already exists
                    throw x.syntaxError("Duplicate key \"" + key + "\"");
                }
                // Only add value if non-null
                Object value = x.nextValue();
                if (value!=null) {
                    put(key, value);
                }
            }

            // Pairs are separated by ','.

            switch (x.nextClean()) {
            case ';':
            case ',':
                if (x.nextClean() == '}') {
                    return;
                }
                x.back();
                break;
            case '}':
                return;
            default:
                throw x.syntaxError("Expected a ',' or '}'");
            }
        }
    }

    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Construct a JSONObject from an XML Document.
   */
    public JSONObject(org.w3c.dom.Document xml) throws JSONException {
        this(javaxt.xml.DOM.getOuterNode(xml));
    }
    
    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Construct a JSONObject from an XML Node.
   */
    public JSONObject(org.w3c.dom.Node node) throws JSONException {
        JSONObject json = new JSONObject();
        if (javaxt.xml.DOM.hasChildren(node)) {
            traverse(node, json);
        }
        else{
            json.setValue(node.getNodeName(), node.getTextContent());
        }
        
        this.map = json.map;
    }
    
    
    private void traverse(org.w3c.dom.Node node, JSONObject json){
        if (node.getNodeType()==1){
            if (javaxt.xml.DOM.hasChildren(node)) {                
                JSONObject _json = new JSONObject();
                org.w3c.dom.NodeList xmlNodeList = node.getChildNodes();
                for (int i=0; i<xmlNodeList.getLength(); i++){
                    traverse(xmlNodeList.item(i), _json);
                }
                json.setValue(node.getNodeName(), _json);
            }
            else{
                json.setValue(node.getNodeName(), node.getTextContent());
            }
        }
    }
    
    
  //**************************************************************************
  //** getValue
  //**************************************************************************
  /** Returns the value associated with a key.
   */
    public Value getValue(String key) {
        if (key == null) return new Value(null);
        return new Value(map.get(key));
    }

    
  //**************************************************************************
  //** getJSONArray
  //**************************************************************************
  /** Returns the JSONArray associated with a key.
   */
    public JSONArray getJSONArray(String key) {
        Object object = map.get(key);
        if (object instanceof JSONArray) {
            return (JSONArray) object;
        }
        return null;
    }

    
  //**************************************************************************
  //** getJSONObject
  //**************************************************************************
  /** Returns the JSONObject associated with a key.
   */
    public JSONObject getJSONObject(String key) {
        Object object = map.get(key);
        if (object instanceof JSONObject) {
            return (JSONObject) object;
        }
        return null;
    }

    
  //**************************************************************************
  //** has
  //**************************************************************************
  /** Returns true if the key exists in the JSONObject.
   */
    public boolean has(String key) {
        return this.map.containsKey(key);
    }


  //**************************************************************************
  //** isNull
  //**************************************************************************
  /** Returns true if there is no value associated with the key.
   */
    public boolean isNull(String key) {
        return map.get(key)==null;
    }

    
  //**************************************************************************
  //** keys
  //**************************************************************************
  /** Returns an enumeration of the keys of the JSONObject. Modifying this key 
   *  Set will also modify the JSONObject. Use with caution.
   */
    public java.util.Iterator<String> keys() {
        return this.keySet().iterator();
    }
    
    
  //**************************************************************************
  //** keySet
  //**************************************************************************
  /** Returns a set of keys of the JSONObject. Modifying this key Set will 
   *  also modify the JSONObject. Use with caution.
   */
    public java.util.Set<String> keySet() {
        return this.map.keySet();
    }

    
  //**************************************************************************
  //** entrySet
  //**************************************************************************
    private java.util.Set<Entry<String, Object>> entrySet() {
        return this.map.entrySet();
    }
    
    
  //**************************************************************************
  //** length
  //**************************************************************************
  /** Returns the number of keys in the JSONObject.
   */
    public int length() {
        return this.map.size();
    }


  //**************************************************************************
  //** setValue
  //**************************************************************************
  /** Used to set the value for a given key with a boolean.
   */
    public void setValue(String key, Boolean value) throws JSONException {
        put(key, value);
    }


  //**************************************************************************
  //** setValue
  //**************************************************************************
  /** Used to set the value for a given key with a double.
   */
    public void setValue(String key, Double value) throws JSONException {
        put(key, value);
    }
    
    
  //**************************************************************************
  //** setValue
  //**************************************************************************
  /** Used to set the value for a given key with a float.
   */
    public void setValue(String key, Float value) throws JSONException {
        put(key, value);
    }

    
  //**************************************************************************
  //** setValue
  //**************************************************************************
  /** Used to set the value for a given key with an integer.
   */
    public void setValue(String key, Integer value) throws JSONException {
        put(key, value);
    }

    
  //**************************************************************************
  //** setValue
  //**************************************************************************
  /** Used to set the value for a given key with a long.
   */
    public void setValue(String key, Long value) throws JSONException {
        put(key, value);
    }


  //**************************************************************************
  //** setValue
  //**************************************************************************
  /** Used to set the value for a given key with a String.
   */
    public void setValue(String key, String str) throws JSONException {
        if (str!=null){
            str = str.trim();
            if (str.length()==0) str = null;
        }
        put(key, str);
    }
    
    
  //**************************************************************************
  //** setValue
  //**************************************************************************
  /** Used to set the value for a given key with a JSONObject.
   */
    public void setValue(String key, JSONObject json) throws JSONException {
        put(key, json);
    }


  //**************************************************************************
  //** setValue
  //**************************************************************************
  /** Used to set the value for a given key with a JSONArray.
   */
    public void setValue(String key, JSONArray arr) throws JSONException {
        put(key, arr);
    }
    
    
  //**************************************************************************
  //** setValue
  //**************************************************************************
  /** Used to set the value for a given key with a javaxt.utils.Value.
   */
    public void setValue(String key, Value val) throws JSONException {
        Object obj = null;
        if (val!=null) obj = val.toObject();
        put(key, obj);
    }
    
    
  //**************************************************************************
  //** put
  //**************************************************************************
  /** Put a key/value pair in the JSONObject. If the value is null, then the
   *  key will be removed from the JSONObject if it is present.
   *  @param key A key string.
   *  @param value An object which is the value. It should be of one of these
   *  types: Boolean, Double, Integer, JSONArray, JSONObject, Long, or String.
   */
    private void put(String key, Object value) throws JSONException {
        if (key == null) {
            throw new NullPointerException("Null key.");
        }
        if (value!=null) {
            testValidity(value);
            map.put(key, value);
        } 
        else {
            map.remove(key);
        }
    }
    
    
  //**************************************************************************
  //** remove
  //**************************************************************************
  /** Remove a name and its value, if present. Returns the value that was 
   *  associated with the name, or null if there was no value.
   */
    public Object remove(String key) {
        return map.remove(key);
    }

    
  //**************************************************************************
  //** equals
  //**************************************************************************
  /** Returns true if the given object is a JSONObject and the JSONObject 
   *  contains the same key/value pairs. Order is not important.
   */
    public boolean equals(Object obj){
        if (obj instanceof JSONObject){
            JSONObject json = (JSONObject) obj;
            if (json.length()==this.length()){
                java.util.Iterator<String> it = map.keySet().iterator();
                while (it.hasNext()){
                    String key = it.next();
                    if (!json.has(key)) return false;
                    Object val = map.get(key);
                    Object val2 = json.getValue(key).toObject();
                    if (val==null){
                        if (val2!=null) return false;
                    }
                    else{
                        if (!val.equals(val2)) return false;
                    }
                }
                return true;
            }
        }
        return false;
    }
    
    
  //**************************************************************************
  //** toString
  //**************************************************************************
  /** Returns the JSONObject as a String. For compactness, no whitespace is
   *  added. If this would not result in a syntactically correct JSON text,
   *  then null will be returned instead.
   */
    @Override
    public String toString() {
        try {
            return this.toString(0);
        } 
        catch (Exception e) {
            return null;
        }
    }
    
    
  //**************************************************************************
  //** toString
  //**************************************************************************
  /** Returns a pretty-printed JSON text of this JSONObject.
   * @param indentFactor The number of spaces to add to each level of indentation.
   */
    public String toString(int indentFactor) {
        try{
            java.io.StringWriter w = new java.io.StringWriter();
            synchronized (w.getBuffer()) {
                return this.write(w, indentFactor, 0).toString();
            }
        }
        catch(Exception e){
            return null;
        }
    }

    
  //**************************************************************************
  //** writeValue
  //**************************************************************************
    protected static final Writer writeValue(Writer writer, Object value,
            int indentFactor, int indent) throws JSONException, IOException {
        if (value == null || value.equals(null)) {
            writer.write("null");
        } else if (value instanceof Number) {
            // not all Numbers may match actual JSON Numbers. i.e. fractions or Imaginary
            final String numberAsString = numberToString((Number) value);
            try {
                // Use the BigDecimal constructor for its parser to validate the format.
                @SuppressWarnings("unused")
                java.math.BigDecimal testNum = new java.math.BigDecimal(numberAsString);
                // Close enough to a JSON number that we will use it unquoted
                writer.write(numberAsString);
            } catch (NumberFormatException ex){
                // The Number value is not a valid JSON number.
                // Instead we will quote it as a string
                quote(numberAsString, writer);
            }
        } else if (value instanceof Boolean) {
            writer.write(value.toString());
        } else if (value instanceof Enum<?>) {
            writer.write(quote(((Enum<?>)value).name()));
        } else if (value instanceof JSONObject) {
            ((JSONObject) value).write(writer, indentFactor, indent);
        } else if (value instanceof JSONArray) {
            ((JSONArray) value).write(writer, indentFactor, indent);
//        } else if (value instanceof Map) {
//            Map<?, ?> map = (Map<?, ?>) value;
//            new JSONObject(map).write(writer, indentFactor, indent);
//        } else if (value instanceof Collection) {
//            Collection<?> coll = (Collection<?>) value;
//            new JSONArray(coll).write(writer, indentFactor, indent);
//        } else if (value.getClass().isArray()) {
//            new JSONArray(value).write(writer, indentFactor, indent);
        } else {
            quote(value.toString(), writer);
        }
        return writer;
    }


  //**************************************************************************
  //** write
  //**************************************************************************
    private Writer write(Writer writer, int indentFactor, int indent)
            throws JSONException {
        try {
            boolean commanate = false;
            final int length = this.length();
            writer.write('{');

            if (length == 1) {
            	final Entry<String,?> entry = this.entrySet().iterator().next();
                final String key = entry.getKey();
                writer.write(quote(key));
                writer.write(':');
                if (indentFactor > 0) {
                    writer.write(' ');
                }
                try{
                    writeValue(writer, entry.getValue(), indentFactor, indent);
                } catch (Exception e) {
                    throw new JSONException("Unable to write JSONObject value for key: " + key, e);
                }
            } else if (length != 0) {
                final int newindent = indent + indentFactor;
                for (final Entry<String,?> entry : this.entrySet()) {
                    if (commanate) {
                        writer.write(',');
                    }
                    if (indentFactor > 0) {
                        writer.write('\n');
                    }
                    indent(writer, newindent);
                    final String key = entry.getKey();
                    writer.write(quote(key));
                    writer.write(':');
                    if (indentFactor > 0) {
                        writer.write(' ');
                    }
                    try {
                        writeValue(writer, entry.getValue(), indentFactor, newindent);
                    } catch (Exception e) {
                        throw new JSONException("Unable to write JSONObject value for key: " + key, e);
                    }
                    commanate = true;
                }
                if (indentFactor > 0) {
                    writer.write('\n');
                }
                indent(writer, indent);
            }
            writer.write('}');
            return writer;
        } catch (IOException exception) {
            throw new JSONException(exception);
        }
    }
    
    
  //**************************************************************************
  //** indent
  //**************************************************************************
    protected static final void indent(Writer writer, int indent) throws IOException {
        for (int i = 0; i < indent; i += 1) {
            writer.write(' ');
        }
    }

    
  //**************************************************************************
  //** testValidity
  //**************************************************************************
    protected static void testValidity(Object o) throws JSONException {
        if (o != null) {
            if (o instanceof Double) {
                if (((Double) o).isInfinite() || ((Double) o).isNaN()) {
                    throw new JSONException(
                            "JSON does not allow non-finite numbers.");
                }
            } else if (o instanceof Float) {
                if (((Float) o).isInfinite() || ((Float) o).isNaN()) {
                    throw new JSONException(
                            "JSON does not allow non-finite numbers.");
                }
            }
        }
    }
    
    
  //**************************************************************************
  //** numberToString
  //**************************************************************************
  /** Produce a string from a Number.
   */
    private static String numberToString(Number number) throws JSONException {
        if (number == null) {
            throw new JSONException("Null pointer");
        }
        testValidity(number);

        // Shave off trailing zeros and decimal point, if possible.

        String string = number.toString();
        if (string.indexOf('.') > 0 && string.indexOf('e') < 0
                && string.indexOf('E') < 0) {
            while (string.endsWith("0")) {
                string = string.substring(0, string.length() - 1);
            }
            if (string.endsWith(".")) {
                string = string.substring(0, string.length() - 1);
            }
        }
        return string;
    }
        
    
  //**************************************************************************
  //** quote
  //**************************************************************************
  /** Returns a String correctly formatted for insertion in a JSON text.
   */
    private static String quote(String string) {
        java.io.StringWriter sw = new java.io.StringWriter();
        synchronized (sw.getBuffer()) {
            try {
                return quote(string, sw).toString();
            } catch (IOException ignored) {
                // will never happen - we are writing to a string writer
                return "";
            }
        }
    }

    private static Writer quote(String string, Writer w) throws IOException {
        if (string == null || string.length() == 0) {
            w.write("\"\"");
            return w;
        }

        char b;
        char c = 0;
        String hhhh;
        int i;
        int len = string.length();

        w.write('"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
            case '\\':
            case '"':
                w.write('\\');
                w.write(c);
                break;
            case '/':
                if (b == '<') {
                    w.write('\\');
                }
                w.write(c);
                break;
            case '\b':
                w.write("\\b");
                break;
            case '\t':
                w.write("\\t");
                break;
            case '\n':
                w.write("\\n");
                break;
            case '\f':
                w.write("\\f");
                break;
            case '\r':
                w.write("\\r");
                break;
            default:
                if (c < ' ' || (c >= '\u0080' && c < '\u00a0')
                        || (c >= '\u2000' && c < '\u2100')) {
                    w.write("\\u");
                    hhhh = Integer.toHexString(c);
                    w.write("0000", 0, 4 - hhhh.length());
                    w.write(hhhh);
                } else {
                    w.write(c);
                }
            }
        }
        w.write('"');
        return w;
    }
}



//******************************************************************************
//**  JSONTokener
//******************************************************************************
/**
 *   A JSONTokener takes a source string and extracts characters and tokens 
 *   from it. It is used by the JSONObject and JSONArray constructors to parse
 *   JSON source strings.
 * 
 *   @author json.org
 *   @version 2014-05-03
 *
 ******************************************************************************/

class JSONTokener {
    
    
    /** current read character position on the current line. */
    private long character;
    /** flag to indicate if the end of the input has been found. */
    private boolean eof;
    /** current read index of the input. */
    private long index;
    /** current line of the input. */
    private long line;
    /** previous character read from the input. */
    private char previous;
    /** Reader for the input. */
    private final java.io.Reader reader;
    /** flag to indicate that a previous character was requested. */
    private boolean usePrevious;
    /** the number of characters read in the previous line. */
    private long characterPreviousLine;



  //**************************************************************************
  //** Constructor
  //**************************************************************************
    protected JSONTokener(String s) {
        java.io.Reader reader = new java.io.StringReader(s);
        this.reader = reader.markSupported()
                ? reader
                        : new java.io.BufferedReader(reader);
        this.eof = false;
        this.usePrevious = false;
        this.previous = 0;
        this.index = 0;
        this.character = 1;
        this.characterPreviousLine = 0;
        this.line = 1;
    }

    

    /**
     * Back up one character. This provides a sort of lookahead capability,
     * so that you can test for a digit or letter before attempting to parse
     * the next number or identifier.
     * @throws JSONException Thrown if trying to step back more than 1 step
     *  or if already at the start of the string
     */
    protected void back() throws JSONException {
        if (this.usePrevious || this.index <= 0) {
            throw new JSONException("Stepping back two steps is not supported");
        }
        this.decrementIndexes();
        this.usePrevious = true;
        this.eof = false;
    }

    /**
     * Decrements the indexes for the {@link #back()} method based on the previous character read.
     */
    private void decrementIndexes() {
        this.index--;
        if(this.previous=='\r' || this.previous == '\n') {
            this.line--;
            this.character=this.characterPreviousLine ;
        } else if(this.character > 0){
            this.character--;
        }
    }

    /**
     * Get the hex value of a character (base16).
     * @param c A character between '0' and '9' or between 'A' and 'F' or
     * between 'a' and 'f'.
     * @return  An int between 0 and 15, or -1 if c was not a hex digit.
     */
    private static int dehexchar(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - ('A' - 10);
        }
        if (c >= 'a' && c <= 'f') {
            return c - ('a' - 10);
        }
        return -1;
    }

    /**
     * Checks if the end of the input has been reached.
     *  
     * @return true if at the end of the file and we didn't step back
     */
    private boolean end() {
        return this.eof && !this.usePrevious;
    }


    /**
     * Determine if the source string still contains characters that next()
     * can consume.
     * @return true if not yet at the end of the source.
     * @throws JSONException thrown if there is an error stepping forward
     *  or backward while checking for more data.
     */
    private boolean more() throws JSONException {
        if(this.usePrevious) {
            return true;
        }
        try {
            this.reader.mark(1);
        } catch (IOException e) {
            throw new JSONException("Unable to preserve stream position", e);
        }
        try {
            // -1 is EOF, but next() can not consume the null character '\0'
            if(this.reader.read() <= 0) {
                this.eof = true;
                return false;
            }
            this.reader.reset();
        } catch (IOException e) {
            throw new JSONException("Unable to read the next character from the stream", e);
        }
        return true;
    }


    /**
     * Get the next character in the source string.
     *
     * @return The next character, or 0 if past the end of the source string.
     * @throws JSONException Thrown if there is an error reading the source string.
     */
    private char next() throws JSONException {
        int c;
        if (this.usePrevious) {
            this.usePrevious = false;
            c = this.previous;
        } else {
            try {
                c = this.reader.read();
            } catch (IOException exception) {
                throw new JSONException(exception);
            }
        }
        if (c <= 0) { // End of stream
            this.eof = true;
            return 0;
        }
        this.incrementIndexes(c);
        this.previous = (char) c;
        return this.previous;
    }

    /**
     * Increments the internal indexes according to the previous character
     * read and the character passed as the current character.
     * @param c the current character read.
     */
    private void incrementIndexes(int c) {
        if(c > 0) {
            this.index++;
            if(c=='\r') {
                this.line++;
                this.characterPreviousLine = this.character;
                this.character=0;
            }else if (c=='\n') {
                if(this.previous != '\r') {
                    this.line++;
                    this.characterPreviousLine = this.character;
                }
                this.character=0;
            } else {
                this.character++;
            }
        }
    }

    /**
     * Consume the next character, and check that it matches a specified
     * character.
     * @param c The character to match.
     * @return The character.
     * @throws JSONException if the character does not match.
     */
    private char next(char c) throws JSONException {
        char n = this.next();
        if (n != c) {
            if(n > 0) {
                throw this.syntaxError("Expected '" + c + "' and instead saw '" +
                        n + "'");
            }
            throw this.syntaxError("Expected '" + c + "' and instead saw ''");
        }
        return n;
    }


    /**
     * Get the next n characters.
     *
     * @param n     The number of characters to take.
     * @return      A string of n characters.
     * @throws JSONException
     *   Substring bounds error if there are not
     *   n characters remaining in the source string.
     */
    private String next(int n) throws JSONException {
        if (n == 0) {
            return "";
        }

        char[] chars = new char[n];
        int pos = 0;

        while (pos < n) {
            chars[pos] = this.next();
            if (this.end()) {
                throw this.syntaxError("Substring bounds error");
            }
            pos += 1;
        }
        return new String(chars);
    }


    /**
     * Get the next char in the string, skipping whitespace.
     * @throws JSONException Thrown if there is an error reading the source string.
     * @return  A character, or 0 if there are no more characters.
     */
    protected char nextClean() throws JSONException {
        for (;;) {
            char c = this.next();
            if (c == 0 || c > ' ') {
                return c;
            }
        }
    }


    /**
     * Return the characters up to the next close quote character.
     * Backslash processing is done. The formal JSON format does not
     * allow strings in single quotes, but an implementation is allowed to
     * accept them.
     * @param quote The quoting character, either
     *      <code>"</code>&nbsp;<small>(double quote)</small> or
     *      <code>'</code>&nbsp;<small>(single quote)</small>.
     * @return      A String.
     * @throws JSONException Unterminated string.
     */
    private String nextString(char quote) throws JSONException {
        char c;
        StringBuilder sb = new StringBuilder();
        for (;;) {
            c = this.next();
            switch (c) {
            case 0:
            case '\n':
            case '\r':
                throw this.syntaxError("Unterminated string");
            case '\\':
                c = this.next();
                switch (c) {
                case 'b':
                    sb.append('\b');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 'u':
                    try {
                        sb.append((char)Integer.parseInt(this.next(4), 16));
                    } catch (NumberFormatException e) {
                        throw this.syntaxError("Illegal escape.", e);
                    }
                    break;
                case '"':
                case '\'':
                case '\\':
                case '/':
                    sb.append(c);
                    break;
                default:
                    throw this.syntaxError("Illegal escape.");
                }
                break;
            default:
                if (c == quote) {
                    return sb.toString();
                }
                sb.append(c);
            }
        }
    }


    /**
     * Get the text up but not including the specified character or the
     * end of line, whichever comes first.
     * @param  delimiter A delimiter character.
     * @return   A string.
     * @throws JSONException Thrown if there is an error while searching
     *  for the delimiter
     */
    private String nextTo(char delimiter) throws JSONException {
        StringBuilder sb = new StringBuilder();
        for (;;) {
            char c = this.next();
            if (c == delimiter || c == 0 || c == '\n' || c == '\r') {
                if (c != 0) {
                    this.back();
                }
                return sb.toString().trim();
            }
            sb.append(c);
        }
    }


    /**
     * Get the text up but not including one of the specified delimiter
     * characters or the end of line, whichever comes first.
     * @param delimiters A set of delimiter characters.
     * @return A string, trimmed.
     * @throws JSONException Thrown if there is an error while searching
     *  for the delimiter
     */
    private String nextTo(String delimiters) throws JSONException {
        char c;
        StringBuilder sb = new StringBuilder();
        for (;;) {
            c = this.next();
            if (delimiters.indexOf(c) >= 0 || c == 0 ||
                    c == '\n' || c == '\r') {
                if (c != 0) {
                    this.back();
                }
                return sb.toString().trim();
            }
            sb.append(c);
        }
    }


    /**
     * Get the next value. The value can be a Boolean, Double, Integer,
     * JSONArray, JSONObject, Long, or String.
     * @throws JSONException If syntax error.
     */
    protected Object nextValue() throws JSONException {
        char c = this.nextClean();
        String string;

        switch (c) {
        case '"':
        case '\'':
            return this.nextString(c);
        case '{':
            this.back();
            return new JSONObject(this);
        case '[':
            this.back();
            return new JSONArray(this);
        }

        /*
         * Handle unquoted text. This could be the values true, false, or
         * null, or it can be a number. An implementation (such as this one)
         * is allowed to also accept non-standard forms.
         *
         * Accumulate characters until we reach the end of the text or a
         * formatting character.
         */

        StringBuilder sb = new StringBuilder();
        while (c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0) {
            sb.append(c);
            c = this.next();
        }
        this.back();

        string = sb.toString().trim();
        if ("".equals(string)) {
            throw this.syntaxError("Missing value");
        }
        return stringToValue(string);
    }


    /**
     * Skip characters until the next character is the requested character.
     * If the requested character is not found, no characters are skipped.
     * @param to A character to skip to.
     * @return The requested character, or zero if the requested character
     * is not found.
     * @throws JSONException Thrown if there is an error while searching
     *  for the to character
     */
    private char skipTo(char to) throws JSONException {
        char c;
        try {
            long startIndex = this.index;
            long startCharacter = this.character;
            long startLine = this.line;
            this.reader.mark(1000000);
            do {
                c = this.next();
                if (c == 0) {
                    // in some readers, reset() may throw an exception if
                    // the remaining portion of the input is greater than
                    // the mark size (1,000,000 above).
                    this.reader.reset();
                    this.index = startIndex;
                    this.character = startCharacter;
                    this.line = startLine;
                    return 0;
                }
            } while (c != to);
            this.reader.mark(1);
        } catch (IOException exception) {
            throw new JSONException(exception);
        }
        this.back();
        return c;
    }

    /**
     * Make a JSONException to signal a syntax error.
     *
     * @param message The error message.
     * @return  A JSONException object, suitable for throwing
     */
    protected JSONException syntaxError(String message) {
        return new JSONException(message + this.toString());
    }

    /**
     * Make a JSONException to signal a syntax error.
     *
     * @param message The error message.
     * @param causedBy The throwable that caused the error.
     * @return  A JSONException object, suitable for throwing
     */
    private JSONException syntaxError(String message, Throwable causedBy) {
        return new JSONException(message + this.toString(), causedBy);
    }

    /**
     * Make a printable string of this JSONTokener.
     *
     * @return " at {index} [character {character} line {line}]"
     */
    @Override
    public String toString() {
        return " at " + this.index + " [character " + this.character + " line " +
                this.line + "]";
    }
    
    
    
  //**************************************************************************
  //** stringToValue
  //**************************************************************************
  /** Try to convert a string into a number, boolean, or null. If the string
   * can't be converted, return the string.
   */
    private static Object stringToValue(String string) {
        if (string.equals("")) {
            return string;
        }
        if (string.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (string.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (string.equalsIgnoreCase("null")) {
            return null;
        }

        /*
         * If it might be a number, try converting it. If a number cannot be
         * produced, then the value will just be a string.
         */

        char initial = string.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            try {
                // if we want full Big Number support this block can be replaced with:
                // return stringToNumber(string);
                if (isDecimalNotation(string)) {
                    Double d = Double.valueOf(string);
                    if (!d.isInfinite() && !d.isNaN()) {
                        return d;
                    }
                } else {
                    Long myLong = Long.valueOf(string);
                    if (string.equals(myLong.toString())) {
                        if (myLong.longValue() == myLong.intValue()) {
                            return Integer.valueOf(myLong.intValue());
                        }
                        return myLong;
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return string;
    }
    
    
  //**************************************************************************
  //** isDecimalNotation
  //**************************************************************************
  /** Tests if the value should be treated as a decimal. It makes no test if 
   *  there are actual digits.
   * 
   *  @return true if the string is "-0" or if it contains '.', 'e', or 'E', 
   *  false otherwise.
   */
    private static boolean isDecimalNotation(final String val) {
        return val.indexOf('.') > -1 || val.indexOf('e') > -1
                || val.indexOf('E') > -1 || "-0".equals(val);
    }
}