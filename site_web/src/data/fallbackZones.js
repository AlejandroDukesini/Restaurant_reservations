export const fallbackZones = [
  {
    id: 1,
    name: "Vistas a la Ventana",
    code: "WINDOW",
    tables: [
      { id: 1, tableNumber: 1, capacity: 2, gridX: 1, gridY: 1, availability: "AVAILABLE" },
      { id: 2, tableNumber: 2, capacity: 2, gridX: 2, gridY: 1, availability: "OCCUPIED" },
      { id: 3, tableNumber: 3, capacity: 4, gridX: 3, gridY: 1, availability: "AVAILABLE" }
    ]
  },
  {
    id: 2,
    name: "Cerca de la Cocina",
    code: "KITCHEN",
    tables: [
      { id: 4, tableNumber: 4, capacity: 4, gridX: 1, gridY: 1, availability: "AVAILABLE" },
      { id: 5, tableNumber: 5, capacity: 6, gridX: 2, gridY: 1, availability: "OCCUPIED" },
      { id: 6, tableNumber: 6, capacity: 2, gridX: 3, gridY: 1, availability: "AVAILABLE" }
    ]
  },
  {
    id: 3,
    name: "Zona Central",
    code: "CENTRAL",
    tables: [
      { id: 7, tableNumber: 7, capacity: 4, gridX: 1, gridY: 1, availability: "AVAILABLE" },
      { id: 8, tableNumber: 8, capacity: 4, gridX: 2, gridY: 1, availability: "AVAILABLE" },
      { id: 9, tableNumber: 9, capacity: 8, gridX: 3, gridY: 1, availability: "OCCUPIED" },
      { id: 10, tableNumber: 10, capacity: 6, gridX: 2, gridY: 2, availability: "AVAILABLE" }
    ]
  },
  {
    id: 4,
    name: "Barra de Cócteles",
    code: "BAR",
    tables: [
      { id: 11, tableNumber: 11, capacity: 2, gridX: 1, gridY: 1, availability: "AVAILABLE" },
      { id: 12, tableNumber: 12, capacity: 2, gridX: 2, gridY: 1, availability: "AVAILABLE" },
      { id: 13, tableNumber: 13, capacity: 2, gridX: 3, gridY: 1, availability: "OCCUPIED" }
    ]
  },
  {
    id: 5,
    name: "Terraza Exterior",
    code: "TERRACE",
    tables: [
      { id: 14, tableNumber: 14, capacity: 4, gridX: 1, gridY: 1, availability: "AVAILABLE" },
      { id: 15, tableNumber: 15, capacity: 6, gridX: 2, gridY: 1, availability: "AVAILABLE" },
      { id: 16, tableNumber: 16, capacity: 2, gridX: 3, gridY: 1, availability: "AVAILABLE" }
    ]
  }
];
