#include "mimalloc-quickjs.h"
#include <mimalloc.h>

static void *js_mi_malloc(JSMallocState *s, size_t size)
{
    return mi_malloc(size);
}

static void js_mi_free(JSMallocState *s, void *ptr)
{
    if (!ptr)
        return;
    mi_free(ptr);
}

static void *js_mi_realloc(JSMallocState *s, void *ptr, size_t size)
{
    return mi_realloc(ptr, size);
}

static const JSMallocFunctions mi_malloc_funcs = {
        js_mi_malloc,
        js_mi_free,
        js_mi_realloc,
        mi_malloc_usable_size
};

JSRuntime *JS_NewRuntimeMimalloc(void)
{
    return JS_NewRuntime2(&mi_malloc_funcs, NULL);
}
