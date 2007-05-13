<!DOCTYPE style-sheet PUBLIC "-//James Clark//DTD DSSSL Style Sheet//EN" [
<!ENTITY % html "IGNORE">
<![%html;[
<!ENTITY % print "IGNORE">
<!ENTITY docbook.dsl PUBLIC "-//Norman Walsh//DOCUMENT DocBook HTML Stylesheet//EN" CDATA dsssl>
]]>
<!ENTITY % print "INCLUDE">
<![%print;[
<!ENTITY docbook.dsl PUBLIC "-//Norman Walsh//DOCUMENT DocBook Print Stylesheet//EN" CDATA dsssl>
]]>
]>

<style-sheet>

<style-specification id="print" use="docbook">
<style-specification-body> 

<!-- PRINT -->

(define %mono-font-family% 
    "Courier New")

(define %title-font-family% 
    "Computer Modern")

(define %body-font-family% 
    "Computer Modern")

(define %admon-font-family% 
    "Computer Modern")

(define %guilabel-font-family%
    "Computer Modern")


(define %hyphenation%
  #t)

(define %linenumber-mod% 
  1)

(define %two-side% 
  #f)

(define %section-autolabel% 
  #t)

(define bop-footnotes
  #t)

(define tex-backend 
  #t)


(define (toc-depth nd)
  3)


<!-- Book -->

(define %generate-book-titlepage%
 #t)

(define %generate-book-toc% 
 #t)

(define %generate-book-titlepage-on-separate-page%
 #t)

(define %generate-part-toc-on-titlepage%
 #t)


<!-- Article -->

(define %generate-article-titlepage%
 #t)

(define %generate-article-toc% 
 #t)

(define %generate-article-titlepage-on-separate-page%
 #t)

</style-specification-body>
</style-specification>

<!-- HTML -->

<style-specification id="html" use="docbook">
<style-specification-body> 

(declare-characteristic preserve-sdata?
          "UNREGISTERED::James Clark//Characteristic::preserve-sdata?"
          #f)


(define %html-pubid% "-//W3C//DTD HTML 4.01//EN")

(define %graphic-extensions% 
'("gif" "png" "jpg" "eps"))

(define %graphic-default-extension% "png")

(define %use-id-as-filename%
 #t)

</style-specification-body>
</style-specification>

<external-specification id="docbook" document="docbook.dsl">

</style-sheet>
