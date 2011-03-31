/**
 * Copyright (c) 2010 Yahoo! Inc. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. See accompanying LICENSE file.
 */
package org.apache.oozie.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Writable;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.binary.Base64;

/**
 * Parses the XML configuration properties for an XConfiguration object.
 */
public class ConfigurationParser
{
    static protected final ClassLoader loader = ClassLoader.getSystemClassLoader();

    private Configuration configuration;

    protected class ArgumentList<E> implements List<E>, Cloneable {
        private List<E> args = new ArrayList();

        private List<Class<E>> types = new ArrayList<Class<E>>();

        public ArgumentList() {
        }

        public ArgumentList(Collection list) {
            addAll(list);
        }

        public List<E> getArgs() {
            return args;
        }

        public List<Class<E>> getTypes() {
            return types;
        }

        @Override
        public boolean add(E e) {
            return args.add(e) && types.add((Class<E>) e.getClass());
        }

        @Override
        public void add(int i, E e) {
            args.add(i, e);
            types.add(i, (Class<E>) e.getClass());
        }

        @Override
        public boolean addAll(Collection<? extends E> clctn) {
            for (E e : clctn) {
                add(e);
            }
            return true;
        }

        @Override
        public boolean addAll(int i, Collection<? extends E> clctn) {
            int j = 0;
            for (E e : clctn) {
                add(i + j++, e);
            }
            return true;
        }

        @Override
        public void clear() {
            args.clear();
            types.clear();
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return new ArgumentList<E>(args);
        }

        @Override
        public boolean contains(Object o) {
            return args.contains((Object) o);
        }

        @Override
        public boolean containsAll(Collection<?> clctn) {
            return args.containsAll(clctn);
        }

        @Override
        public E get(int i) {
            return args.get(i);
        }

        @Override
        public int indexOf(Object o) {
            return args.indexOf(o);
        }

        @Override
        public boolean isEmpty() {
            return args.isEmpty();
        }

        @Override
        public Iterator<E> iterator() {
            return args.iterator();
        }

        @Override
        public int lastIndexOf(Object o) {
            return args.lastIndexOf(o);
        }

        @Override
        public ListIterator<E> listIterator() {
            return args.listIterator();
        }

        @Override
        public ListIterator<E> listIterator(int i) {
            return args.listIterator(i);
        }

        @Override
        public boolean remove(Object o) {
            return args.remove(o) && types.remove(o.getClass());
        }

        @Override
        public E remove(int i) {
            types.remove(i);
            return args.remove(i);
        }

        @Override
        public boolean removeAll(Collection<?> clctn) {
            for (Object o : clctn) {
                types.remove(o.getClass());
            }
            return args.removeAll(clctn);
        }

        @Override
        public boolean retainAll(Collection<?> clctn) {
            for (Class<E> type : types) {
                if (!clctn.contains(type)) {
                    types.remove(type);
                }
            }
            return args.removeAll(clctn);
        }

        @Override
        public E set(int i, E e) {
            types.set(i, (Class<E>) e.getClass());
            return args.set(i, e);
        }

        @Override
        public int size() {
            return args.size();
        }

        @Override
        public List<E> subList(int start, int end) {
            List<E> sub = null;
            try {
                sub = (List<E>) clone();

                while (end > start) {
                    sub.remove(start);
                    end--;
                }

            } catch (ClassCastException e) {
                // not possible
            } catch (CloneNotSupportedException e) {
                // not possible
            }

            return sub;
        }

        @Override
        public Object[] toArray() {
            return args.toArray();
        }

        @Override
        public <E> E[] toArray(E[] ts) {
            return args.toArray(ts);
        }

        public Object[] toTypeArray() {
            return types.toArray();
        }

        public Class<E>[] toTypeArray(Class<E>[] ts) {
            return types.toArray(ts);
        }
    }

    public ConfigurationParser(Configuration configuration) {
        this.configuration = configuration;
    }

    public void parseDocument(Document doc) throws IOException {
        try {
            Element root = doc.getDocumentElement();
            if (!"configuration".equals(root.getTagName())) {
                throw new IOException("bad conf file: top-level element not <configuration>");
            }
            parseProperties(root.getChildNodes());
        }
        catch (DOMException e) {
            throw new IOException(e);
        }
    }

    public void parseProperties(NodeList props) throws IOException, DOMException {
        for (int i = 0; i < props.getLength(); i++) {
            Node propNode = props.item(i);
            if (!(propNode instanceof Element)) {
                continue;
            }
            parseProperty((Element) propNode);
        }
    }

    public void parseProperty(Element prop) throws IOException, DOMException {
        if (!"property".equals(prop.getTagName())) {
            throw new IOException("bad conf file: element not <property>");
        }
        NodeList fields = prop.getChildNodes();
        String attr = null;
        String value = null;
        for (int i = 0; i < fields.getLength(); i++) {
            Node fieldNode = fields.item(i);
            if (!(fieldNode instanceof Element)) {
                continue;
            }
            Element field = (Element) fieldNode;
            if ("name".equals(field.getTagName()) && field.hasChildNodes()) {
                attr = ((Text) field.getFirstChild()).getData().trim();
            } else
            if ("value".equals(field.getTagName()) && field.hasChildNodes()) {
                value = ((Text) field.getFirstChild()).getData();
            } else
            if ("object".equals(field.getTagName()) && field.hasChildNodes()) {
                value = parseObject(field.getAttribute("class"), field.getChildNodes());
            }
        }

        if (attr != null && value != null) {
            configuration.set(attr, value);
        }
    }

    public String parseObject(String className, NodeList nodes) throws IOException {
        Writable object = null;

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element element = (Element) node;

            // load class for object
            Class<Writable> cls;
            try {
                cls = (Class<Writable>) loader.loadClass(className);
            } catch (ClassNotFoundException e) {
                throw new IOException("bad conf file: class '" + className + "' not found");
            }

            // instanciate object
            try {
                if ("constructor".equals(element.getTagName()) && element.hasChildNodes()) {
                    // with constructor
                    ArgumentList args = parseArgs(cls, element.getChildNodes());
                    Class<Writable>[] types = (Class<Writable>[]) args.getTypes().toArray(new Class[0]);
                    try {
                        object = cls.getConstructor(types).newInstance(args.getArgs());
                    } catch (NoSuchMethodException e) {
                        throw new IOException("bad conf file: constructor for " +
                                className + " does not exist with specified types");
                    } catch (InvocationTargetException e) {
                        throw new IOException("bad conf file: error instanciating " +
                                className + ": " + e.getTargetException().getMessage());
                    }
                } else {
                    object = cls.newInstance();
                }
            } catch (InstantiationException e) {
                throw new IOException("bad conf file: failed to instanciate " +
                        className + ": " + e.getMessage());
            } catch (IllegalAccessException e) {
                throw new IOException("bad conf file: compatible constructor for " +
                        className + " is not public");
            }
        }

        if (object != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            object.write(dos);
            return new String(Base64.encodeBase64(out.toByteArray()));
        }
        throw new IOException("bad conf file: unable to create object from " + className);
    }

    public ArgumentList parseArgs(Class<Writable> cls, NodeList nodes) throws IOException {
        ArgumentList args = new ArgumentList();

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element arg = (Element) node;
            String type = arg.getAttribute("type");
            String value = arg.getTextContent();

            if (!"".equals(type)) {
                try {
                    args.add(castTo(value, type));
                } catch (IllegalArgumentException e) {
                    throw new IOException(e.getMessage());
                }
            }
        }

        return args;
    }

    public Object castTo(String value, String type) {
        if ("String".equals(type)) {
            return value;
        } else
        if ("byte[]".equals(type)) {
            return value.getBytes();
        } else
        if ("char[]".equals(type)) {
            return value.toCharArray();
        } else
        if ("int".equals(type)) {
            return new Integer(value);
        } else
        if ("short".equals(type)) {
            return new Short(value);
        } else
        if ("long".equals(type)) {
            return new Long(value);
        } else
        if ("float".equals(type)) {
            return new Float(value);
        } else
        if ("double".equals(type)) {
            return new Double(value);
        } else
        if ("boolean".equals(type)) {
            return new Boolean(value);
        }

        throw new IllegalArgumentException("bad conf file: '" + type + "' is not a permitted type");
    }
}
