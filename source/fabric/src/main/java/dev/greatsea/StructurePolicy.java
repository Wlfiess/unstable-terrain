package dev.greatsea;
import java.io.IOException;
import java.util.Properties;
/** Controls the optional Temple, Empire and Orbital landmarks. */
public final class StructurePolicy {
 private static final boolean ENABLED=load();
 private StructurePolicy(){}
 public static boolean enabled(){return ENABLED;}
 private static boolean load(){
  try(var in=StructurePolicy.class.getResourceAsStream("/assets/unstableterrain/distribution.properties")){
   if(in==null)return true;
   var properties=new Properties();properties.load(in);
   return Boolean.parseBoolean(properties.getProperty("structures","true"));
  }catch(IOException e){throw new ExceptionInInitializerError(e);}
 }
}
