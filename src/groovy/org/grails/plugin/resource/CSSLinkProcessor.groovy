package org.grails.plugin.resource

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * This class is used to parse out and replace CSS links
 *
 * @author Marc Palmer (marc@grailsrocks.com)
 * @author Luke Daley (ld@ldaley.com)
 */
class CSSLinkProcessor {

    private Logger log = LoggerFactory.getLogger(getClass())

    // We need to successfully match any kind of @import and url(), mappers are responsible for checking type
    static final CSS_URL_PATTERN = ~/(?:(@import\s*['"])(.+?)(['"]))|(url\(\s*['"]?)(.+?)(['"]?\s*\))/

    boolean isCSSRewriteCandidate(ResourceMeta resource, ResourceProcessor grailsResourceProcessor) {
        boolean enabled = grailsResourceProcessor.config.rewrite.css instanceof Boolean ? grailsResourceProcessor.config.rewrite.css : true
        boolean yes = enabled && (resource.contentType == "text/css" || resource.tagAttributes?.type == "css")
        if (log.debugEnabled) {
            log.debug "Resource $resource.actualUrl being CSS rewritten? $yes"
        }
        return yes
    }

    /**
     * Find all url() and fix up the url if it is not absolute
     * NOTE: This needs to run after any plugins that move resources around, but before any that obliterate
     * the content i.e. before minify or gzip
     */
    void process(ResourceMeta resource, ResourceProcessor grailsResourceProcessor, Closure urlMapper) {

        if (!isCSSRewriteCandidate(resource, grailsResourceProcessor)) {
            if (log.debugEnabled) {
                log.debug "CSS link processor skipping $resource because its not a CSS rewrite candidate"
            }
            return
        }

        // Move existing to tmp file, then write to the correct file
        File origFileTempCopy = new File(resource.processedFile.toString()+'.tmp')

        // Make sure temp file doesn't exist already
        new File(origFileTempCopy.toString()).delete() // On MS Windows if we don't do this origFileTempCopy gets corrupt after delete

        // Move the existing file to temp
        resource.processedFile.renameTo(origFileTempCopy)

        if (log.debugEnabled) {
            log.debug "Pre-processing CSS resource $resource.sourceUrl to rewrite links"
        }

        String inputCss = origFileTempCopy.getText('UTF-8')
        def processedCss = inputCss.replaceAll(CSS_URL_PATTERN) { Object[] args ->
            int modifier = args[1] ? 0 : 3 // determine: @import or url() match
            def prefix = args[1 + modifier]
            String originalUrl = args[2 + modifier].trim()
            def suffix = args[3 + modifier]

            return urlMapper(prefix, originalUrl, suffix)
        }
        resource.processedFile.setText(processedCss, 'UTF-8')

        // Delete the temp file
        origFileTempCopy.delete()
    }
}
