/*
The MIT License (MIT)

Copyright (c) 2015 Pierre Lindenbaum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


History:
* 2014 creation

*/
package com.github.lindenb.jvarkit.tools.blast;

import java.io.PrintWriter;
import java.util.Collection;

import gov.nih.nlm.ncbi.blast.Iteration;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import htsjdk.samtools.util.CloserUtil;

import com.github.lindenb.jvarkit.io.IOUtils;



public class ReduceBlast extends AbstractReduceBlast
{
	private static final org.slf4j.Logger LOG = com.github.lindenb.jvarkit.util.log.Logging.getLog(ReduceBlast.class);

	private Unmarshaller unmarshaller;
	private Marshaller marshaller;
	/* force javac to compile */
	@SuppressWarnings("unused")
	private gov.nih.nlm.ncbi.blast.ObjectFactory _ignore_for_javac=null;
	
		
	
	private void run(
			final XMLEventReader r,
			final XMLEventWriter w
			)
			throws XMLStreamException,JAXBException
		{
		final XMLEventFactory eventFactory=XMLEventFactory.newFactory();

		boolean prev_was_iteration=false;
		while(r.hasNext())
			{
			final XMLEvent evt=r.peek();
			QName qname=null;
			if(evt.isStartElement() && (qname=evt.asStartElement().getName()).getLocalPart().equals("Iteration")) 
				{
				final Iteration iteration =  this.unmarshaller.unmarshal(r, Iteration.class).getValue();
				if(!iteration.getIterationHits().getHit().isEmpty()) {
					if(!super.keep_message) iteration.setIterationMessage(null);
					if(!super.keep_stats) iteration.setIterationStat(null);
					this.marshaller.marshal(
							new JAXBElement<Iteration>(qname,Iteration.class , iteration),
							w);
					w.add(eventFactory.createCharacters("\n"));
					}
				prev_was_iteration=true;
				continue;
				}
			else if(prev_was_iteration && evt.isCharacters() && is_empty(evt.asCharacters().getData())) {
				r.nextEvent();
				continue;
				}
				
			w.add(r.nextEvent());
			prev_was_iteration=false;
			}
		}
	
	private static boolean is_empty(final String s) {
		if(s==null ) return true;
		for(int i=0;i< s.length();i++)
			{
			if(!Character.isWhitespace(s.charAt(i))) return false;
			}
		return true;
	}

	@Override
	protected Collection<Throwable> call(String inputName) throws Exception {
		PrintWriter pw=null;
		XMLEventReader rx=null;
		XMLEventWriter wx=null;
		try
			{
			
			final JAXBContext jc = JAXBContext.newInstance("gov.nih.nlm.ncbi.blast");
			this.unmarshaller=jc.createUnmarshaller();
			this.marshaller=jc.createMarshaller();
			this.marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT,true);
			this.marshaller.setProperty(Marshaller.JAXB_FRAGMENT,true);
			final XMLInputFactory xmlInputFactory=XMLInputFactory.newFactory();
			xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
			xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
			xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
			xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
			xmlInputFactory.setXMLResolver(new XMLResolver()
				{
				@Override
				public Object resolveEntity(String arg0, String arg1, String arg2,
						String arg3) throws XMLStreamException
					{
					LOG.info("resolveEntity:" +arg0+"/"+arg1+"/"+arg2);
					return null;
					}
				});
			if(inputName==null)
				{
				LOG.info("Reading from stdin");
				rx=xmlInputFactory.createXMLEventReader(stdin());
				}
			else
				{
				LOG.info("Reading from "+inputName);
				rx=xmlInputFactory.createXMLEventReader(IOUtils.openURIForBufferedReading(inputName));
				}
			final XMLOutputFactory xmlOutputFactory= XMLOutputFactory.newFactory();
			pw = super.openFileOrStdoutAsPrintWriter();
			wx = xmlOutputFactory.createXMLEventWriter(pw);
			run(rx,wx);
			wx.close();
			pw.flush();
			return RETURN_OK;
			}
		catch(Exception err)
			{
			return wrapException(err);
			}
		finally
			{
			CloserUtil.close(rx);
			CloserUtil.close(wx);
			CloserUtil.close(pw);
			}
		}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new ReduceBlast().instanceMainWithExit(args);

	}

}
