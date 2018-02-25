package com.tterrag.k9.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.tterrag.k9.mcp.IMapping.Side;
import com.tterrag.k9.mcp.ISrgMapping.MappingType;

public class MappingDatabase {
        
    private final Multimap<MappingType, IMapping> mappings = MultimapBuilder.enumKeys(MappingType.class).arrayListValues().build();
    
    private final File zip;
    
    private final SrgDatabase srgs;
    
    public MappingDatabase(String mcver) throws NoSuchVersionException {
        File folder = Paths.get(".", "data", mcver, "mappings").toFile();
        if (!folder.exists()) {
            throw new NoSuchVersionException(mcver);
        }
        this.zip = folder.listFiles()[0];
        try {
            reload();
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        
        this.srgs = DataDownloader.INSTANCE.getSrgDatabase(mcver);
    }

    public void reload() throws IOException {
        mappings.clear();
        ZipFile zipfile = new ZipFile(zip);

        try {
            for (MappingType type : MappingType.values()) {
                if (type.getCsvName() != null) {
                    List<String> lines;
                    lines = IOUtils.readLines(zipfile.getInputStream(zipfile.getEntry(type.getCsvName() + ".csv")), Charsets.UTF_8);

                    lines.remove(0); // Remove header line

                    for (String line : lines) {
                        String[] info = line.split(",", -1);
                        String comment = info.length > 3 ? Joiner.on(',').join(ArrayUtils.subarray(info, 3, info.length)) : "";
                        IMapping mapping = new IMapping.Impl(type, info[0], info[1], comment, Side.values()[Integer.valueOf(info[2])]);
                        mappings.put(type, mapping);
                    }
                }
            }
        } finally {
            zipfile.close();
        }
    }

    public List<IMapping> lookup(MappingType type, String name) {
        Collection<IMapping> mappingsForType = mappings.get(type);
        String[] hierarchy = null;
        if (name.contains(".")) {
            hierarchy = name.split("\\.");
            name = hierarchy[hierarchy.length - 1];
        }

        final String lookup = name;
        List<IMapping> ret = mappingsForType.stream().filter(m -> m.getSRG().contains(lookup) || m.getMCP().equals(lookup)).collect(Collectors.toList());
        if (hierarchy != null) {
            if (type == MappingType.PARAM) {
                // TODO
            } else {
                final String parent = hierarchy[0];
                ret = ret.stream().filter(m -> Strings.nullToEmpty(srgs.lookup(type, m.getSRG()).get(0).getOwner()).endsWith(parent)).collect(Collectors.toList());
            }
        }
        
        return ret;
    }
}
