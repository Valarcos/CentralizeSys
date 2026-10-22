package com.centralizesys.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    static final String API_PATH_PATTERN = "/api/**";
    public static final String EXTERNAL_API_PATH_PATTERN = "/api/external/**";

    private final MaintenanceInterceptor maintenanceInterceptor;

    public WebMvcConfig(MaintenanceInterceptor maintenanceInterceptor) {
        this.maintenanceInterceptor = maintenanceInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Registered FIRST so that the 503 produced by MaintenanceInterceptor
        // (which short-circuits the chain) also carries no-store and can never be cached.
        // /api/external/** is excluded so that external B2B integration controllers
        // can define authoritative Cache-Control (e.g., max-age=900) and ETag headers.
        registry.addInterceptor(buildApiCacheControlInterceptor())
                .addPathPatterns(API_PATH_PATTERN)
                .excludePathPatterns(EXTERNAL_API_PATH_PATTERN);
        registry.addInterceptor(maintenanceInterceptor);
    }

    /**
     * Safe-by-default cache policy: every transactional API response is non-cacheable.
     * Package-private so it can be unit-tested without a Spring context (project "Gray Box" strategy).
     * Future public endpoints can be opened declaratively here, e.g.:
     * interceptor.addCacheMapping(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic(), "/api/public/**");
     * (Spring uses the most specific matching pattern; keep any such entry out of "/api/auth/**".)
     */
    static WebContentInterceptor buildApiCacheControlInterceptor() {
        WebContentInterceptor interceptor = new WebContentInterceptor();
        interceptor.addCacheMapping(CacheControl.noStore().mustRevalidate(), API_PATH_PATTERN);
        return interceptor;
    }

    // TODO: [ARQUITECTURA / ZONA HORARIA JACKSON - DECISIÓN D-6] Unificación de formato de fecha en serialización JSON.
    // -----------------------------------------------------------------------------------------------------------------
    // Cambio necesario:
    // Configurar globalmente en application.properties 'spring.jackson.time-zone=America/Argentina/Buenos_Aires'
    // o declarar un Bean Jackson2ObjectMapperBuilderCustomizer para que tanto entidades con LocalDateTime (hoy emitidas
    // en formato ISO naive 'YYYY-MM-DDTHH:mm:ss') como consultas SQL con java.sql.Timestamp (hoy emitidas con offset UTC
    // '+00:00' o 'Z') se serialicen con idéntico estándar canónico en todas las respuestas REST.
    //
    // Consecuencias sistémicas para el sistema:
    // Modificar la serialización de Jackson impacta a nivel global en TODOS los endpoints del sistema (VentaResponse,
    // ClienteDto, Deudores, Cheques, Auditoría, Reportes). Cambiar la estructura de las cadenas de fecha puede alterar
    // o romper selectores de fecha en la UI, bibliotecas de gráficos o clientes externos que dependan del formato actual.
    //
    // Medidas de seguridad y prueba necesarias antes de aplicarlo:
    // 1. Auditoría exhaustiva de todos los DTOs y controladores REST para catalogar qué tipos temporales devuelven.
    // 2. Pruebas de integración frontend para verificar que filtros de rango de fechas (SalesHistory, Reportes, CobrosYPedidos)
    //    sigan parseando correctamente las respuestas sin desfases.
    // 3. Verificación de renderizado de comprobantes PDF en cliente (jsPDF / pdfGenerator.js) y exportaciones de reportes.
    // 4. Verificación de interoperabilidad con zonas horarias en clientes móviles fuera de Argentina.
}
