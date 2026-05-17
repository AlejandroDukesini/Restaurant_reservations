package com.restaurant.reservations.service

import com.restaurant.reservations.model.Restaurant
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

@Service
class WebsiteGeneratorService(
    @Value("\${app.generated-websites-path}") private val generatedWebsitesPath: String
) {
    
    fun generateWebsite(restaurant: Restaurant): String {
        val websiteDir = Paths.get(generatedWebsitesPath, restaurant.slug).toFile()
        
        if (!websiteDir.exists()) {
            websiteDir.mkdirs()
        }
        
        generateIndexHtml(restaurant, websiteDir)
        generateStylesCss(restaurant, websiteDir)
        generateTablesJs(restaurant, websiteDir)
        
        return "/${restaurant.slug}"
    }
    
    private fun generateIndexHtml(restaurant: Restaurant, websiteDir: File) {
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="description" content="Reserva tu mesa en ${restaurant.name}">
                <title>${restaurant.name} - Reserva Tu Mesa</title>
                <link rel="stylesheet" href="styles.css">
                <script src="https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4"></script>
            </head>
            <body class="bg-gray-100">
                <header class="bg-red-600 text-white py-4">
                    <div class="container mx-auto px-4">
                        <h1 class="text-3xl font-bold">${restaurant.name}</h1>
                        <p class="text-sm">${restaurant.description}</p>
                    </div>
                </header>
                
                <main class="container mx-auto px-4 py-8">
                    <section id="restaurant-info" class="mb-8">
                        <div class="bg-white rounded-lg shadow p-6">
                            <h2 class="text-2xl font-bold mb-4">Información del Restaurante</h2>
                            <p><strong>Dirección:</strong> ${restaurant.address}</p>
                            <p><strong>Teléfono:</strong> ${restaurant.phone}</p>
                            <p><strong>Email:</strong> ${restaurant.email}</p>
                            <p><strong>Mesas:</strong> ${restaurant.numberOfTables}</p>
                            <p><strong>Sillas:</strong> ${restaurant.numberOfChairs}</p>
                            <p><strong>Pisos:</strong> ${restaurant.numberOfFloors}</p>
                        </div>
                    </section>
                    
                    <section id="tables" class="mb-8">
                        <div class="bg-white rounded-lg shadow p-6">
                            <h2 class="text-2xl font-bold mb-4">Selecciona tu Mesa</h2>
                            <div id="floor-selector" class="mb-4">
                                ${generateFloorSelector(restaurant.numberOfFloors)}
                            </div>
                            <div id="tables-container" class="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <!-- Tables will be loaded dynamically -->
                            </div>
                        </div>
                    </section>
                    
                    <section id="reservation-form" class="mb-8 hidden">
                        <div class="bg-white rounded-lg shadow p-6">
                            <h2 class="text-2xl font-bold mb-4">Reservar Mesa</h2>
                            <form id="reservationForm">
                                <input type="hidden" id="tableId" name="tableId">
                                <div class="mb-4">
                                    <label class="block mb-2">Fecha y Hora:</label>
                                    <input type="datetime-local" id="reservationDate" name="reservationDate" class="w-full p-2 border rounded" required>
                                </div>
                                <div class="mb-4">
                                    <label class="block mb-2">Número de Invitados:</label>
                                    <input type="number" id="numberOfGuests" name="numberOfGuests" class="w-full p-2 border rounded" required min="1">
                                </div>
                                <div class="mb-4">
                                    <label class="block mb-2">Solicitudes Especiales:</label>
                                    <textarea id="specialRequests" name="specialRequests" class="w-full p-2 border rounded" rows="3"></textarea>
                                </div>
                                <button type="submit" class="bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700">Confirmar Reserva</button>
                            </form>
                        </div>
                    </section>
                </main>
                
                <footer class="bg-gray-800 text-white py-4 mt-8">
                    <div class="container mx-auto px-4 text-center">
                        <p>&copy; ${java.time.Year.now().value} ${restaurant.name}. Todos los derechos reservados.</p>
                    </div>
                </footer>
                
                <script src="tables.js"></script>
            </body>
            </html>
        """.trimIndent()
        
        File(websiteDir, "index.html").writeText(htmlContent)
    }
    
    private fun generateStylesCss(restaurant: Restaurant, websiteDir: File) {
        val cssContent = """
            body {
                font-family: Arial, sans-serif;
            }
            
            .table-card {
                border: 2px solid #e5e7eb;
                border-radius: 8px;
                padding: 16px;
                transition: all 0.3s ease;
                cursor: pointer;
            }
            
            .table-card:hover {
                border-color: #dc2626;
                transform: translateY(-2px);
                box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
            }
            
            .table-card.selected {
                border-color: #dc2626;
                background-color: #fef2f2;
            }
            
            .floor-btn {
                margin-right: 8px;
                padding: 8px 16px;
                border: 2px solid #dc2626;
                background: white;
                color: #dc2626;
                border-radius: 4px;
                cursor: pointer;
                transition: all 0.3s ease;
            }
            
            .floor-btn:hover, .floor-btn.active {
                background: #dc2626;
                color: white;
            }
            
            .hidden {
                display: none;
            }
        """.trimIndent()
        
        File(websiteDir, "styles.css").writeText(cssContent)
    }
    
    private fun generateTablesJs(restaurant: Restaurant, websiteDir: File) {
        val jsContent = """
            const API_URL = '/api';
            const RESTAURANT_SLUG = '${restaurant.slug}';
            let selectedTable = null;
            let currentFloor = 1;
            
            // Initialize
            document.addEventListener('DOMContentLoaded', function() {
                loadTables(currentFloor);
                setupFloorSelector();
                setupReservationForm();
            });
            
            function setupFloorSelector() {
                const floorButtons = document.querySelectorAll('.floor-btn');
                floorButtons.forEach(btn => {
                    btn.addEventListener('click', function() {
                        floorButtons.forEach(b => b.classList.remove('active'));
                        this.classList.add('active');
                        currentFloor = parseInt(this.dataset.floor);
                        loadTables(currentFloor);
                    });
                });
            }
            
            function loadTables(floor) {
                fetch(API_URL + '/public/restaurants/' + RESTAURANT_SLUG + '/tables?floor=' + floor)
                    .then(response => response.json())
                    .then(tables => {
                        const container = document.getElementById('tables-container');
                        container.innerHTML = tables.map(table => 
                            '<div class="table-card" data-table-id="' + table.id + '" onclick="selectTable(' + table.id + ', ' + table.tableNumber + ', ' + table.price + ', ' + table.capacity + ')">' +
                            '<h3 class="text-lg font-bold">Mesa ' + table.tableNumber + '</h3>' +
                            '<p>Piso: ' + table.floor + '</p>' +
                            '<p>Capacidad: ' + table.capacity + ' personas</p>' +
                            '<p class="text-red-600 font-bold">' + table.price.toFixed(2) + ' COP</p>' +
                            '</div>'
                        ).join('');
                    })
                    .catch(error => console.error('Error loading tables:', error));
            }
            
            function selectTable(tableId, tableNumber, price, capacity) {
                selectedTable = { id: tableId, tableNumber, price, capacity };
                
                document.querySelectorAll('.table-card').forEach(card => {
                    card.classList.remove('selected');
                    if (parseInt(card.dataset.tableId) === tableId) {
                        card.classList.add('selected');
                    }
                });
                
                document.getElementById('tableId').value = tableId;
                document.getElementById('numberOfGuests').max = capacity;
                document.getElementById('reservation-form').classList.remove('hidden');
            }
            
            function setupReservationForm() {
                document.getElementById('reservationForm').addEventListener('submit', function(e) {
                    e.preventDefault();
                    
                    const token = localStorage.getItem('token');
                    if (!token) {
                        alert('Debes iniciar sesión para hacer una reserva');
                        return;
                    }
                    
                    const reservationData = {
                        tableId: parseInt(document.getElementById('tableId').value),
                        reservationDate: document.getElementById('reservationDate').value,
                        numberOfGuests: parseInt(document.getElementById('numberOfGuests').value),
                        specialRequests: document.getElementById('specialRequests').value || null
                    };
                    
                    fetch(API_URL + '/customer/reservations', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'Authorization': 'Bearer ' + token
                        },
                        body: JSON.stringify(reservationData)
                    })
                    .then(response => response.json())
                    .then(data => {
                        alert('¡Reserva creada exitosamente!');
                        document.getElementById('reservation-form').classList.add('hidden');
                    })
                    .catch(error => {
                        console.error('Error creating reservation:', error);
                        alert('Error al crear la reserva');
                    });
                });
            }
        """.trimIndent()
        
        File(websiteDir, "tables.js").writeText(jsContent)
    }
    
    private fun generateFloorSelector(numberOfFloors: Int): String {
        return (1..numberOfFloors).map { floor ->
            """<button class="floor-btn ${if (floor == 1) "active" else ""}" data-floor="$floor">Piso $floor</button>"""
        }.joinToString("\n")
    }
}
